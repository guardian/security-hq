package aws.iam

import aws.AwsAsyncHandler.awsToScala
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.identitymanagement.model.{GetCredentialReportRequest, GetCredentialReportResult}
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagement, AmazonIdentityManagementAsync, AmazonIdentityManagementAsyncClientBuilder}
import model.IAMCredentialsReport

import scala.concurrent.{ExecutionContext, Future}


object IAMClient {
  def client(profile: String, region: Region = Region.getRegion(Regions.EU_WEST_1)): AmazonIdentityManagementAsync = {
    val credentialsProvider = new AWSCredentialsProviderChain(
      InstanceProfileCredentialsProvider.getInstance(),
      new ProfileCredentialsProvider(profile)
    )
    AmazonIdentityManagementAsyncClientBuilder.standard()
      .withCredentials(credentialsProvider)
      .withRegion(Option(Regions.getCurrentRegion).getOrElse(region).getName).build()
  }

  def getCredentialsReport(client: AmazonIdentityManagementAsync)(implicit ec: ExecutionContext): Future[IAMCredentialsReport] = {
    val request = new GetCredentialReportRequest()
    awsToScala(client.getCredentialReportAsync)(request).map(CredentialsReport.extractReport)
  }
}
