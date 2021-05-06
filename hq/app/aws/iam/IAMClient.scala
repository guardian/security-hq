package aws.iam

import aws.AwsAsyncHandler._
import aws.cloudformation.CloudFormation
import aws.{AwsClient, AwsClients}
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{GenerateCredentialReportRequest, GenerateCredentialReportResult, GetCredentialReportRequest}
import logic.{CredentialsReportDisplay, Retry}
import model.{AwsAccount, CredentialReportDisplay, IAMCredentialsReport}
import org.joda.time.DateTime
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


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
        enrichedReport = CredentialsReport.enrichReportWithStackDetails(report, stacks)
      } yield {
        CredentialsReportDisplay.toCredentialReportDisplay(enrichedReport)
      }
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