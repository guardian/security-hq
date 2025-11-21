package aws.iam

import aws.AwsAsyncHandler.*
import aws.cloudformation.CloudFormation
import aws.{AwsAsyncHandler, AwsClient, AwsClients, AwsClientsList}
import logic.{CredentialsReportDisplay, Retry}
import model.*
import org.joda.time.DateTime
import play.api.Logging
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.{Tag, *}
import software.amazon.awssdk.services.s3.S3AsyncClient
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*


object IAMClient extends Logging {

  val SOLE_REGION = Region.of("us-east-1")

  private def generateCredentialsReport(client: AwsClient[IamAsyncClient])(implicit ec: ExecutionContext): Attempt[GenerateCredentialReportResponse] = {
    val request = GenerateCredentialReportRequest.builder.build()
    handleAWSErrs(client)(asScala(client.client.generateCredentialReport(request)))
  }

  private def getCredentialsReport(client: AwsClient[IamAsyncClient])(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val request = GetCredentialReportRequest.builder.build()
    handleAWSErrs(client)(asScala(client.client.getCredentialReport(request))).flatMap(CredentialsReport.extractReport)
  }

  /**
    * Attempts to update 'credential' with tags fetched from AWS. If the request to AWS fails, return the original credential
    * @return Updated or original credential
    */
  private def enrichCredentialWithTags(credential: IAMCredential, client: AwsClient[IamAsyncClient])(implicit ec: ExecutionContext) = {
    val request = ListUserTagsRequest.builder.userName(credential.user).build()
    val result = asScala(client.client.listUserTags(request))
    result.map { tagsResult =>
      val tagsList = tagsResult.tags.asScala.toList.map(t => model.Tag(t.key, t.value))
      credential.copy(tags = tagsList)
    }
      // If the request to fetch tags fails, just return the original user
      .recover { case error =>
        logger.warn(s"Failed to fetch tags for user ${credential.user}. Storing user without tags.", error)
        credential
      }
  }

  private def enrichReportWithTags(report: IAMCredentialsReport, client: AwsClient[IamAsyncClient])(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val updatedEntries = Future.sequence(report.entries.map(e => {
      // the root user isn't a normal IAM user - exclude from tag lookup
      if (!IAMCredential.isRootUser(e.user)) {
        enrichCredentialWithTags(e, client)
      } else
        Future.successful(e)
    }))
    val updatedReport = updatedEntries.map(e => report.copy(entries = e))
    // Convert to an Attempt
    Attempt.fromFuture(updatedReport){
      case throwable => Failure(throwable.getMessage, "failed to enrich report with tags", 500, throwable = Some(throwable)).attempt
    }
  }

  def getCredentialReportDisplay(
    account: AwsAccount,
    currentData: Either[FailedAttempt, CredentialReportDisplay],
    cfnClients: AwsClients[CloudFormationAsyncClient],
    iamClients: AwsClients[IamAsyncClient],
    regions: List[Region]
  )(implicit ec: ExecutionContext): Attempt[CredentialReportDisplay] = {
    val delay = 3.seconds
    val now = DateTime.now()

    if(CredentialsReport.credentialsReportReadyForRefresh(currentData, now))
      for {
        client <- iamClients.get(account, SOLE_REGION)
        _ <- Retry.until(generateCredentialsReport(client), CredentialsReport.isComplete, "Failed to generate credentials report", delay)
        report <- getCredentialsReport(client)
        stacks <- CloudFormation.getStacksFromAllRegions(account, cfnClients, regions)
        reportWithTags <- enrichReportWithTags(report, client)
        reportWithStacks = CredentialsReport.enrichReportWithStackDetails(reportWithTags, stacks)
      } yield CredentialsReportDisplay.toCredentialReportDisplay(reportWithStacks)
    else
      Attempt.fromEither(currentData)
  }

  def getAllCredentialReports(
    accounts: Seq[AwsAccount],
    currentData: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]],
    cfnClients: AwsClients[CloudFormationAsyncClient],
    iamClients: AwsClients[IamAsyncClient],
    regions: List[Region]
  )(implicit executionContext: ExecutionContext): Attempt[Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        getCredentialReportDisplay(account, currentData(account), cfnClients, iamClients, regions).asFuture.map(account -> _)
      }
    }
  }

  def listUserAccessKeys(account: AwsAccount, user: IAMUser, iamClients: AwsClients[IamAsyncClient])(implicit ec: ExecutionContext): Attempt[List[CredentialMetadata]] = {
    for {
      client <- iamClients.get(account, SOLE_REGION)
      result <- listAccessKeys(client, user)
      keyMetdatas = result.accessKeyMetadata.asScala.toList
      credentialMetadatas <- Attempt.traverse(keyMetdatas) { akm =>
        for {
          credentialStatus <- akm.status match {
            case StatusType.ACTIVE =>
              Attempt.Right (CredentialActive)
            case StatusType.INACTIVE =>
              Attempt.Right (CredentialDisabled)
            case StatusType.UNKNOWN_TO_SDK_VERSION =>
              Attempt.Left {
                Failure (
                  s"Could not create credential metadata from status value, as it is unknown to SDK version (expected 'Active' or 'Inactive')",
                  "Couldn't lookup AWS Access Key metadata",
                  500
                )
              }
            case StatusType.EXPIRED =>
              Attempt.Left {
                Failure (
                  s"Could not create credential metadata from status value, as it is expired (expected 'Active' or 'Inactive')",
                  "Couldn't lookup AWS Access Key metadata",
                  500
                )
              }
          }
        } yield CredentialMetadata(akm.userName, akm.accessKeyId, new DateTime(akm.createDate), credentialStatus)
      }
    } yield credentialMetadatas
  }

  private def listAccessKeys(client: AwsClient[IamAsyncClient], user: IAMUser)(implicit ec: ExecutionContext): Attempt[ListAccessKeysResponse] = {
    val request = ListAccessKeysRequest.builder.userName(user.username).build()
    handleAWSErrs(client)(asScala(client.client.listAccessKeys(request)))
  }

  def disableAccessKey(awsAccount: AwsAccount, username: String, accessKeyId: String, iamClients: AwsClients[IamAsyncClient])(implicit ec: ExecutionContext): Attempt[UpdateAccessKeyResponse] = {
    val request = UpdateAccessKeyRequest.builder
      .userName(username)
      .accessKeyId(accessKeyId)
      .status("Inactive")
      .build()
    for {
      client <- iamClients.get(awsAccount, SOLE_REGION)
      result <- handleAWSErrs(client)(asScala(client.client.updateAccessKey(request)))
    } yield result
  }

  private def handleDeleteLoginProfileErrs(awsClient: AwsClient[IamAsyncClient], username: String)(f: => Future[DeleteLoginProfileResponse])(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResponse]] =
    AwsAsyncHandler.handleAWSErrs(awsClient)(f.map(Some.apply).recover({
      case e if e.getMessage.contains(s"Login Profile for User $username cannot be found") => None
      case _: NoSuchEntityException => None
    }))

  def deleteLoginProfile(awsAccount: AwsAccount, username: String, iamClients: AwsClients[IamAsyncClient])(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResponse]] = {
    val request = DeleteLoginProfileRequest.builder.userName(username).build()
    for {
      client <- iamClients.get(awsAccount, SOLE_REGION)
      result <- handleDeleteLoginProfileErrs(client, username)(asScala(client.client.deleteLoginProfile(request)))
    } yield result
  }
}
