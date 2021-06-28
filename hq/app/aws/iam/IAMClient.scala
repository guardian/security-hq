package aws.iam

import aws.AwsAsyncHandler._
import aws.cloudformation.CloudFormation
import aws.{AwsClient, AwsClients}
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{GenerateCredentialReportRequest, GenerateCredentialReportResult, GetCredentialReportRequest, ListUserTagsRequest}
import logic.{CredentialsReportDisplay, Retry}
import org.joda.time.DateTime
import model.{AwsAccount, CredentialReportDisplay, IAMCredential, IAMCredentialsReport, Tag}
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
}