package aws

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
  def credentialsProvider(account: AwsAccount): AWSCredentialsProviderChain = {
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

  private def client[T, S <: AwsClientBuilder[S, T]](
    account: AwsAccount,
    region: Regions,
    builder: AwsClientBuilder[S, T]
  ): ((String, Regions), T) = {
    val auth = credentialsProvider(account)
    val client = builder
      .withCredentials(auth)
      .withRegion(region)
      .build()
    (account.id, region) -> client
  }

  private def clients[T, S <: AwsClientBuilder[S, T]](
    configuration: Configuration,
    regionList: List[Regions],
    builder: AwsClientBuilder[S, T]
  ): Map[(String, Regions), T] = {
    val list = for {
      account <- Config.getAwsAccounts(configuration)
      region <- regionList
    } yield client[T, S](account, region, builder)
    Map(list: _*)
  }

  // Only needs Regions.EU_WEST_1
  def inspectorClients(configuration: Configuration): Map[(String, Regions), AmazonInspectorAsync] =
    clients[AmazonInspectorAsync, AmazonInspectorAsyncClientBuilder](
      configuration,
      List(Regions.EU_WEST_1),
      AmazonInspectorAsyncClientBuilder.standard())

  def ec2Clients(configuration: Configuration): Map[(String, Regions), AmazonEC2Async] =
    clients[AmazonEC2Async, AmazonEC2AsyncClientBuilder](
      configuration,
      Regions.values().toList,
      AmazonEC2AsyncClientBuilder.standard())

  def cfnClients(configuration: Configuration): Map[(String, Regions), AmazonCloudFormationAsync] =
    clients[AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder](
      configuration,
      Regions.values().toList,
      AmazonCloudFormationAsyncClientBuilder.standard())

  // Only needs Regions.US_EAST_1
  def taClients(configuration: Configuration): Map[(String, Regions), AWSSupportAsync] =
    clients[AWSSupportAsync, AWSSupportAsyncClientBuilder](
      configuration,
      List(Regions.US_EAST_1),
      AWSSupportAsyncClientBuilder.standard())

  def iamClients(configuration: Configuration): Map[(String, Regions), AmazonIdentityManagementAsync] =
    clients[AmazonIdentityManagementAsync, AmazonIdentityManagementAsyncClientBuilder](
      configuration,
      Regions.values().toList,
      AmazonIdentityManagementAsyncClientBuilder.standard())

}
