package aws.iam

import aws.AwsAsyncHandler._
import aws.cloudformation.CloudFormation
import aws.{AwsAsyncHandler, AwsClient, AwsClients}
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileRequest, DeleteLoginProfileResult, GenerateCredentialReportRequest, GenerateCredentialReportResult, GetCredentialReportRequest, ListAccessKeysRequest, ListAccessKeysResult, ListUserTagsRequest, NoSuchEntityException, UpdateAccessKeyRequest, UpdateAccessKeyResult}
import logic.{CredentialsReportDisplay, Retry}
import model.{AwsAccount, CredentialActive, CredentialDisabled, CredentialMetadata, CredentialReportDisplay, HumanUser, IAMCredential, IAMCredentialsReport, IAMUser, Tag}
import org.joda.time.DateTime
import play.api.Logging
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._


object IAMClient extends Logging {

  val SOLE_REGION = Regions.US_EAST_1

  private def generateCredentialsReport(client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[GenerateCredentialReportResult] = {
    val request = new GenerateCredentialReportRequest()
    handleAWSErrs(client)(awsToScala(client)(_.generateCredentialReportAsync)(request))
  }

  private def getCredentialsReport(client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val request = new GetCredentialReportRequest()
    handleAWSErrs(client)(awsToScala(client)(_.getCredentialReportAsync)(request)).flatMap(CredentialsReport.extractReport)
  }

  /**
    * Attempts to update 'credential' with tags fetched from AWS. If the request to AWS fails, return the original credential
    * @return Updated or original credential
    */
  private def enrichCredentialWithTags(credential: IAMCredential, client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext) = {
    val request = new ListUserTagsRequest().withUserName(credential.user)
    val result = awsToScala(client)(_.listUserTagsAsync)(request)
    result.map { tagsResult =>
      val tagsList = tagsResult.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
      credential.copy(tags = tagsList)
    }
      // If the request to fetch tags fails, just return the original user
      .recover { case error =>
        logger.warn(s"Failed to fetch tags for user ${credential.user}. Storing user without tags.", error)
        credential
      }
  }

  private def enrichReportWithTags(report: IAMCredentialsReport, client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
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
    cfnClients: AwsClients[AmazonCloudFormationAsync],
    iamClients: AwsClients[AmazonIdentityManagementAsync],
    regions: List[Regions]
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
    cfnClients: AwsClients[AmazonCloudFormationAsync],
    iamClients: AwsClients[AmazonIdentityManagementAsync],
    regions: List[Regions]
  )(implicit executionContext: ExecutionContext): Attempt[Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        getCredentialReportDisplay(account, currentData(account), cfnClients, iamClients, regions).asFuture.map(account -> _)
      }
    }
  }

  def listUserAccessKeys(account: AwsAccount, user: IAMUser, iamClients: AwsClients[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[List[CredentialMetadata]] = {
    for {
      client <- iamClients.get(account, SOLE_REGION)
      result <- listAccessKeys(client, user)
      keyMetdatas = result.getAccessKeyMetadata.asScala.toList
      credentialMetadatas <- Attempt.traverse(keyMetdatas) { akm =>
        for {
          credentialStatus <- akm.getStatus match {
            case "Active" =>
              Attempt.Right (CredentialActive)
            case "Inactive" =>
              Attempt.Right (CredentialDisabled)
            case unexpected =>
              Attempt.Left {
                Failure (
                  s"Could not create credential metadata from unexpected status value $unexpected (expected 'Active' or 'Inactive')",
                  "Couldn't lookup AWS Access Key metadata",
                  500
                )
              }
          }
        } yield CredentialMetadata(akm.getUserName, akm.getAccessKeyId, new DateTime(akm.getCreateDate), credentialStatus)
      }
    } yield credentialMetadatas
  }

  private def listAccessKeys(client: AwsClient[AmazonIdentityManagementAsync], user: IAMUser)(implicit ec: ExecutionContext): Attempt[ListAccessKeysResult] = {
    val request = new ListAccessKeysRequest().withUserName(user.username)
    handleAWSErrs(client)(awsToScala(client)(_.listAccessKeysAsync)(request))
  }

  def disableAccessKey(awsAccount: AwsAccount, username: String, accessKeyId: String, iamClients: AwsClients[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[UpdateAccessKeyResult] = {
    val request = new UpdateAccessKeyRequest()
      .withUserName(username)
      .withAccessKeyId(accessKeyId)
      .withStatus("Inactive")
    for {
      client <- iamClients.get(awsAccount, SOLE_REGION)
      result <- handleAWSErrs(client)(awsToScala(client)(_.updateAccessKeyAsync)(request))
    } yield result
  }

  private def handleDeleteLoginProfileErrs(awsClient: AwsClient[AmazonIdentityManagementAsync], username: String)(f: => Future[DeleteLoginProfileResult])(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] =
    AwsAsyncHandler.handleAWSErrs(awsClient)(f.map(Some.apply).recover({
      case e if e.getMessage.contains(s"Login Profile for User $username cannot be found") => None
      case _: NoSuchEntityException => None
    }))

  def deleteLoginProfile(awsAccount: AwsAccount, username: String, iamClients: AwsClients[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] = {
    val request = new DeleteLoginProfileRequest()
      .withUserName(username)
    for {
      client <- iamClients.get(awsAccount, SOLE_REGION)
      result <- handleDeleteLoginProfileErrs(client, username)(awsToScala(client)(_.deleteLoginProfileAsync)(request))
    } yield result
  }
}