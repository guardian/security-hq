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
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._


object IAMClient {

  val SOLE_REGION = Regions.US_EAST_1

  private def generateCredentialsReport(client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[GenerateCredentialReportResult] = {
    val request = new GenerateCredentialReportRequest()
    handleAWSErrs(client)(awsToScala(client)(_.generateCredentialReportAsync)(request))
  }

  private def getCredentialsReport(client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val request = new GetCredentialReportRequest()
    handleAWSErrs(client)(awsToScala(client)(_.getCredentialReportAsync)(request)).flatMap(CredentialsReport.extractReport)
  }

  private def enrichCredentialWithTags(credential: IAMCredential, client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext) = {
    val request = new ListUserTagsRequest().withUserName(credential.user)
    val result = awsToScala(client)(_.listUserTagsAsync)(request)
    result.map { tagsResult =>
      val tagsList = tagsResult.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
      credential.copy(tags = tagsList)
    }
  }

  private def enrichReportWithTags(report: IAMCredentialsReport, client: AwsClient[AmazonIdentityManagementAsync])(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val updatedEntries = handleAWSErrs(client)(Future.sequence(report.entries.map(e => enrichCredentialWithTags(e, client))))
    val updatedReportAttempt = updatedEntries.map(e => report.copy(entries = e))
    // if the fetch tags request failed, just return the original report without tags
    Attempt.fromFuture(updatedReportAttempt.fold(_ => report, updatedReport => updatedReport)){
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