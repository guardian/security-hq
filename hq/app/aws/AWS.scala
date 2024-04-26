package aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentialsProviderChain, STSAssumeRoleSessionCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.client.builder.{AwsAsyncClientBuilder, AwsClientBuilder}
import com.amazonaws.regions.{Region, RegionUtils, Regions}
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import com.amazonaws.services.elasticfilesystem.{AmazonElasticFileSystemAsync, AmazonElasticFileSystemAsyncClientBuilder}
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagementAsync, AmazonIdentityManagementAsyncClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.simplesystemsmanagement.{AWSSimpleSystemsManagement, AWSSimpleSystemsManagementClientBuilder}
import com.amazonaws.services.support.{AWSSupportAsync, AWSSupportAsyncClientBuilder}
import config.Config
import model.{AwsAccount, DEV, PROD, Stage}
import play.api.Configuration
import utils.attempt.{Attempt, Failure}

import java.util.concurrent.Executors.newCachedThreadPool

object AWS {

  def lookupAccount(accountId: String, accounts: List[AwsAccount]): Attempt[AwsAccount] = {
    Attempt.fromOption(
      accounts.find(_.id == accountId),
      Failure.awsAccountNotFound(accountId).attempt
    )
  }

  private def credentialsProvider(account: AwsAccount): AWSCredentialsProviderChain = {
    new AWSCredentialsProviderChain(
      new STSAssumeRoleSessionCredentialsProvider.Builder(account.roleArn, "security-hq").build(),
      new ProfileCredentialsProvider(account.id)
    )
  }

  private[aws] def clients[A, B <: AwsClientBuilder[B, A]](
    builder: AwsClientBuilder[B, A],
    configuration: Configuration,
    regionList: Region*
  ): AwsClients[A] = {
    for {
      account <- Config.getAwsAccounts(configuration)
      region <- regionList
      client = builder
        .withCredentials(credentialsProvider(account))
        .withRegion(region.getName)
        .withClientConfiguration(new ClientConfiguration().withMaxConnections(10))
        .build()
    } yield AwsClient(client, account, region)
  }

  private def withCustomThreadPool[A, B <: AwsAsyncClientBuilder[B, A]] = (asyncClientBuilder: AwsAsyncClientBuilder[B, A]) =>
    asyncClientBuilder.withExecutorFactory(() => newCachedThreadPool())

  def ec2Clients(configuration: Configuration, regions: List[Region]): AwsClients[AmazonEC2Async] =
    clients(withCustomThreadPool(AmazonEC2AsyncClientBuilder.standard()), configuration, regions:_*)

  def cfnClients(configuration: Configuration, regions: List[Region]): AwsClients[AmazonCloudFormationAsync] =
    clients(withCustomThreadPool(AmazonCloudFormationAsyncClientBuilder.standard()), configuration, regions:_*)

  // Only needs Regions.US_EAST_1
  def taClients(configuration: Configuration, region: Region = RegionUtils.getRegion("us-east-1")): AwsClients[AWSSupportAsync] =
    clients(withCustomThreadPool(AWSSupportAsyncClientBuilder.standard()), configuration, region)

  def s3Clients(configuration: Configuration, regions: List[Region]): AwsClients[AmazonS3] =
    clients(AmazonS3ClientBuilder.standard(), configuration, regions:_*)

  def iamClients(configuration: Configuration, regions: List[Region]): AwsClients[AmazonIdentityManagementAsync] =
    clients(withCustomThreadPool(AmazonIdentityManagementAsyncClientBuilder.standard()), configuration, regions:_*)

  def efsClients(configuration: Configuration, regions: List[Region]): AwsClients[AmazonElasticFileSystemAsync] =
    clients(withCustomThreadPool(AmazonElasticFileSystemAsyncClientBuilder.standard()), configuration, regions:_*)
}
