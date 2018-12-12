package aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, STSAssumeRoleSessionCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder}
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagementAsync, AmazonIdentityManagementAsyncClientBuilder}
import com.amazonaws.services.inspector.{AmazonInspectorAsync, AmazonInspectorAsyncClientBuilder}
import com.amazonaws.services.support.{AWSSupportAsync, AWSSupportAsyncClientBuilder}
import config.Config
import model.AwsAccount
import play.api.Configuration
import utils.attempt.{Attempt, Failure}


object AWS {

  private def credentialsProvider(account: AwsAccount): AWSCredentialsProviderChain = {
    new AWSCredentialsProviderChain(
      new STSAssumeRoleSessionCredentialsProvider.Builder(account.roleArn, "security-hq").build(),
      new ProfileCredentialsProvider(account.id)
    )
  }

  def lookupAccount(accountId: String, accounts: List[AwsAccount]): Attempt[AwsAccount] = {
    Attempt.fromOption(
      accounts.find(_.id == accountId),
      Failure.awsAccountNotFound(accountId).attempt
    )
  }

  def regions: List[Regions] = Regions.values().filterNot(r => r.getName.startsWith("CN_") || r.getName == "GovCloud").toList

  private[aws] def clients[A, B <: AwsClientBuilder[B, A]](
    builder: AwsClientBuilder[B, A],
    configuration: Configuration,
    regionList: Regions*
  ): Map[(String, Regions), A] = {
    val list = for {
      account <- Config.getAwsAccounts(configuration)
      region <- regionList
      client = builder
        .withCredentials(credentialsProvider(account))
        .withRegion(region)
        .withClientConfiguration(new ClientConfiguration().withMaxConnections(10))
        .build()
    } yield (account.id, region) -> client
    list.toMap
  }

  // Only needs Regions.EU_WEST_1
  def inspectorClients(configuration: Configuration): Map[(String, Regions), AmazonInspectorAsync] =
    clients(AmazonInspectorAsyncClientBuilder.standard(), configuration, Regions.EU_WEST_1)

  def ec2Clients(configuration: Configuration): Map[(String, Regions), AmazonEC2Async] =
    clients(AmazonEC2AsyncClientBuilder.standard(), configuration, regions:_*)

  def cfnClients(configuration: Configuration): Map[(String, Regions), AmazonCloudFormationAsync] =
    clients(AmazonCloudFormationAsyncClientBuilder.standard(), configuration, regions:_*)

  // Only needs Regions.US_EAST_1
  def taClients(configuration: Configuration): Map[(String, Regions), AWSSupportAsync] =
    clients(AWSSupportAsyncClientBuilder.standard(), configuration, Regions.US_EAST_1)

  def iamClients(configuration: Configuration): Map[(String, Regions), AmazonIdentityManagementAsync] =
    clients(AmazonIdentityManagementAsyncClientBuilder.standard(), configuration, regions:_*)

}
