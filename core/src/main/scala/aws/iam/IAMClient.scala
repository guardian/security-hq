package aws.iam

import aws.AwsAsyncHandler.*
import aws.{AwsAsyncHandler, AwsClient, AwsClients, AwsClientsList}
import com.typesafe.scalalogging.LazyLogging
import logic.{CredentialsReportDisplay, Retry}
import model.*
import org.joda.time.DateTime
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.*
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

object IAMClient extends LazyLogging {

  val SOLE_REGION: Region = Region.of("us-east-1")

  /*
   * Note: the report is actually generated a maximum of once every 4 hours.
   * Even if it has a status of COMPLETE, that doesn't mean it's fresh.
   * The GetCredentialsReportResponse.generatedAt field will tell you when it was actually generated.
   *
   * See https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_getting-report.html.
   */
  private def generateCredentialsReport(
      client: AwsClient[IamAsyncClient]
  )(implicit ec: ExecutionContext): Attempt[GenerateCredentialReportResponse] = {
    val request = GenerateCredentialReportRequest.builder.build()
    handleAWSErrs(client)(asScala(client.client.generateCredentialReport(request)))
  }

  private def getCredentialsReport(
      client: AwsClient[IamAsyncClient]
  )(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val request = GetCredentialReportRequest.builder.build()
    handleAWSErrs(client)(asScala(client.client.getCredentialReport(request))).flatMap(CredentialsReport.extractReport)
  }

  /** Attempts to update 'credential' with tags fetched from AWS. If the request to AWS fails, return the original
    * credential
    * @return
    *   Updated or original credential
    */
  private def enrichCredentialWithTags(credential: IAMCredential, client: AwsClient[IamAsyncClient])(implicit
      ec: ExecutionContext
  ) = {
    val request = ListUserTagsRequest.builder.userName(credential.user).build()
    val result = asScala(client.client.listUserTags(request))
    result
      .map { tagsResult =>
        val tagsList = tagsResult.tags.asScala.toList.map(t => model.Tag(t.key, t.value))
        credential.copy(tags = tagsList)
      }
      // If the request to fetch tags fails, just return the original user
      .recover { case error =>
        error.getCause match {
          case _: NoSuchEntityException =>
            logger.info(
              s"User ${credential.user} has been deleted since the report was generated. Storing user without tags."
            )
          case _ =>
            logger.warn(s"Failed to fetch tags for user ${credential.user}. Storing user without tags.", error)
        }
        credential
      }
  }

  private def enrichReportWithTags(report: IAMCredentialsReport, client: AwsClient[IamAsyncClient])(implicit
      ec: ExecutionContext
  ): Attempt[IAMCredentialsReport] = {
    val updatedEntries = Future.sequence(report.entries.map(e => {
      // the root user isn't a normal IAM user - exclude from tag lookup
      if (!IAMCredential.isRootUser(e.user)) {
        enrichCredentialWithTags(e, client)
      } else
        Future.successful(e)
    }))
    val updatedReport = updatedEntries.map(e => report.copy(entries = e))
    // Convert to an Attempt
    Attempt.fromFuture(updatedReport) { case throwable =>
      Failure(throwable.getMessage, throwable = Some(throwable)).attempt
    }
  }

  private def getCredentialReportDisplay(
      account: AwsAccount,
      currentData: Either[FailedAttempt, CredentialReportDisplay],
      iamClients: AwsClients[IamAsyncClient]
  )(implicit ec: ExecutionContext): Attempt[CredentialReportDisplay] = {
    val now = DateTime.now()

    if (CredentialsReport.credentialsReportReadyForRefresh(currentData, now))
      getUpdatedCredentialsReport(account, iamClients)
    else
      Attempt.fromEither(currentData)
  }

  def getUpdatedCredentialsReport(
      account: AwsAccount,
      iamClients: AwsClients[IamAsyncClient],
      delay: FiniteDuration = 3.seconds
  )(using ExecutionContext): Attempt[CredentialReportDisplay] = {
    for {
      client <- iamClients.get(account)
      _ <- Retry.until(
        generateCredentialsReport(client),
        CredentialsReport.isComplete,
        s"Failed to generate credentials report for account $account",
        delay
      )
      report <- getCredentialsReport(client)
      reportWithTags <- enrichReportWithTags(report, client)
    } yield CredentialsReportDisplay.toCredentialReportDisplay(reportWithTags)
  }

  def getAllCredentialReports(
      accounts: Seq[AwsAccount],
      currentData: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]],
      iamClients: AwsClients[IamAsyncClient]
  )(implicit
      executionContext: ExecutionContext
  ): Attempt[Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        getCredentialReportDisplay(account, currentData(account), iamClients).asFuture
          .map(account -> _)
      }
    }
  }

  def listUserAccessKeys(account: AwsAccount, user: IAMUser, iamClients: AwsClients[IamAsyncClient])(implicit
      ec: ExecutionContext
  ): Attempt[List[CredentialMetadata]] = {
    for {
      client <- iamClients.get(account)
      result <- listAccessKeys(client, user)
      keyMetdatas = result.accessKeyMetadata.asScala.toList
      credentialMetadatas <- Attempt.traverse(keyMetdatas) { akm =>
        for {
          credentialStatus <- akm.status match {
            case StatusType.ACTIVE =>
              Attempt.Right(CredentialActive)
            case StatusType.INACTIVE =>
              Attempt.Right(CredentialDisabled)
            case StatusType.UNKNOWN_TO_SDK_VERSION =>
              Attempt.Left {
                Failure(
                  s"Could not create credential metadata from status value, as it is unknown to SDK version (expected 'Active' or 'Inactive')"
                )
              }
            case StatusType.EXPIRED =>
              Attempt.Left {
                Failure(
                  s"Could not create credential metadata from status value, as it is expired (expected 'Active' or 'Inactive')"
                )
              }
          }
        } yield CredentialMetadata(
          akm.userName,
          akm.accessKeyId,
          new DateTime(akm.createDate.toEpochMilli),
          credentialStatus
        )
      }
    } yield credentialMetadatas
  }

  private def listAccessKeys(client: AwsClient[IamAsyncClient], user: IAMUser)(implicit
      ec: ExecutionContext
  ): Attempt[ListAccessKeysResponse] = {
    val request = ListAccessKeysRequest.builder.userName(user.username).build()
    handleAWSErrs(client)(asScala(client.client.listAccessKeys(request)))
  }

  def disableAccessKey(
      awsAccount: AwsAccount,
      username: String,
      accessKeyId: String,
      iamClients: AwsClients[IamAsyncClient]
  )(implicit ec: ExecutionContext): Attempt[UpdateAccessKeyResponse] = {
    val request = UpdateAccessKeyRequest.builder
      .userName(username)
      .accessKeyId(accessKeyId)
      .status("Inactive")
      .build()
    for {
      client <- iamClients.get(awsAccount)
      result <- handleAWSErrs(client)(asScala(client.client.updateAccessKey(request)))
      _ = logger.info(
        s"Disabled access key $accessKeyId for IAM user $username in account ${awsAccount.name}."
      )
    } yield result
  }

  private def handleDeleteLoginProfileErrs(awsClient: AwsClient[IamAsyncClient], username: String)(
      f: => Future[DeleteLoginProfileResponse]
  )(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResponse]] =
    AwsAsyncHandler.handleAWSErrs(awsClient)(
      f.map(Some.apply)
        .recover({
          case e if e.getMessage.contains(s"Login Profile for User $username cannot be found") => None
          case _: NoSuchEntityException                                                        => None
        })
    )

  def deleteLoginProfile(awsAccount: AwsAccount, username: String, iamClients: AwsClients[IamAsyncClient])(implicit
      ec: ExecutionContext
  ): Attempt[Option[DeleteLoginProfileResponse]] = {
    val request = DeleteLoginProfileRequest.builder.userName(username).build()
    for {
      client <- iamClients.get(awsAccount)
      result <- handleDeleteLoginProfileErrs(client, username)(asScala(client.client.deleteLoginProfile(request)))
      message =
        if (result.isDefined) {
          s"Deleted login profile for IAM user $username in account ${awsAccount.name}."
        } else {
          s"No login profile found for IAM user $username in account ${awsAccount.name}; nothing to delete."
        }
      _ = logger.info(message)
    } yield result
  }

}
