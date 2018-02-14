package aws.iam

import aws.AWS
import aws.AwsAsyncHandler._
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.identitymanagement.model.{GenerateCredentialReportRequest, GenerateCredentialReportResult, GetCredentialReportRequest}
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagementAsync, AmazonIdentityManagementAsyncClientBuilder}
import logic.{ReportDisplay, Retry}
import model.{AwsAccount, CredentialReportDisplay, IAMCredentialsReport}
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


object IAMClient {
  private def client(account: AwsAccount, region: Region = Region.getRegion(Regions.EU_WEST_1)): AmazonIdentityManagementAsync = {
    val auth = AWS.credentialsProvider(account)
    AmazonIdentityManagementAsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(Option(Regions.getCurrentRegion).getOrElse(region).getName).build()
  }

  private def generateCredentialsReport(client: AmazonIdentityManagementAsync)(implicit ec: ExecutionContext): Attempt[GenerateCredentialReportResult] = {
    val request = new GenerateCredentialReportRequest()
    handleAWSErrs(awsToScala(client.generateCredentialReportAsync)(request))
  }

  private def getCredentialsReport(client: AmazonIdentityManagementAsync)(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val request = new GetCredentialReportRequest()
    handleAWSErrs(awsToScala(client.getCredentialReportAsync)(request)).flatMap(CredentialsReport.extractReport)
  }

  def getCredentialsReport(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[CredentialReportDisplay] = {
    val delay = 3.seconds
    val client = IAMClient.client(account)
    for {
      _ <- Retry.until(generateCredentialsReport(client), CredentialsReport.isComplete, "Failed to generate credentials report", delay)
      report <- getCredentialsReport(client)
    } yield ReportDisplay.toCredentialReportDisplay(report)
  }

  def getAllCredentialReports(accounts: Seq[AwsAccount])(implicit executionContext: ExecutionContext): Attempt[Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        getCredentialsReport(account).asFuture.map(account -> _)
      }
    }
  }


}