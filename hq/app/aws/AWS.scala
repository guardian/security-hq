package aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, STSAssumeRoleSessionCredentialsProvider}
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

  // Only needs Regions.EU_WEST_1
  def inspectorClients(configuration: Configuration): Map[(String, Regions), AmazonInspectorAsync] = Config.getAwsAccounts(configuration).flatMap(account => Regions.values().map(
    region => {
      val auth = credentialsProvider(account)
      val inspectorClient = AmazonInspectorAsyncClientBuilder.standard()
        .withCredentials(auth)
        .withRegion(region)
        .build()
      (account.id, region) -> inspectorClient
    }
  )).toMap


  def ec2Clients(configuration: Configuration): Map[(String, Regions), AmazonEC2Async] = Config.getAwsAccounts(configuration).flatMap(account => Regions.values().map(
    region => {
      val auth = credentialsProvider(account)
      val inspectorClient = AmazonEC2AsyncClientBuilder.standard()
        .withCredentials(auth)
        .withRegion(region)
        .build()
      (account.id, region) -> inspectorClient
    }
  )).toMap

  def cfnClients(configuration: Configuration): Map[(String, Regions), AmazonCloudFormationAsync] = Config.getAwsAccounts(configuration).flatMap(account => Regions.values().map(
    region => {
      val auth = credentialsProvider(account)
      val cloudFormationClient = AmazonCloudFormationAsyncClientBuilder.standard()
        .withCredentials(auth)
        .withRegion(region)
        .build()
      (account.id, region) -> cloudFormationClient
    }
  )).toMap

  // Only needs Regions.US_EAST_1
  def taClients(configuration: Configuration): Map[(String, Regions), AWSSupportAsync] = Config.getAwsAccounts(configuration).flatMap(account => List(Regions.US_EAST_1).map(
    region => {
      val auth = credentialsProvider(account)
      val cloudFormationClient = AWSSupportAsyncClientBuilder.standard()
        .withCredentials(auth)
        .withRegion(region)
        .build()
      (account.id, region) -> cloudFormationClient
    }
  )).toMap

  def iamClients(configuration: Configuration): Map[(String, Regions), AmazonIdentityManagementAsync] = Config.getAwsAccounts(configuration).flatMap(account => List(Regions.US_EAST_1).map(
    region => {
      val auth = credentialsProvider(account)
      val cloudFormationClient = AmazonIdentityManagementAsyncClientBuilder.standard()
        .withCredentials(auth)
        .withRegion(region)
        .build()
      (account.id, region) -> cloudFormationClient
    }
  )).toMap

}
