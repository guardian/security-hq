package aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentialsProviderChain, AWSStaticCredentialsProvider, BasicAWSCredentials, STSAssumeRoleSessionCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import com.amazonaws.services.elasticfilesystem.{AmazonElasticFileSystemAsync, AmazonElasticFileSystemAsyncClient, AmazonElasticFileSystemAsyncClientBuilder}
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagementAsync, AmazonIdentityManagementAsyncClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.sns.{AmazonSNSAsync, AmazonSNSAsyncClientBuilder}
import com.amazonaws.services.support.{AWSSupportAsync, AWSSupportAsyncClientBuilder}
import config.Config
import model._
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

  private[aws] def clients[A, B <: AwsClientBuilder[B, A]](
    builder: AwsClientBuilder[B, A],
    configuration: Configuration,
    regionList: Regions*
  ): AwsClients[A] = {
    for {
      account <- Config.getAwsAccounts(configuration)
      region <- regionList
      client = builder
        .withCredentials(credentialsProvider(account))
        .withRegion(region)
        .withClientConfiguration(new ClientConfiguration().withMaxConnections(10))
        .build()
    } yield AwsClient(client, account, region)
  }

  def dynamoDbClient(securityCredentialsProvider: AWSCredentialsProvider, region: Regions, stage: Stage): AmazonDynamoDB = {
    stage match {
      case PROD =>
        AmazonDynamoDBClientBuilder.standard()
          .withCredentials(securityCredentialsProvider)
          .withRegion(region)
          .build()
      case DEV | TEST =>
        AmazonDynamoDBClientBuilder.standard()
          .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("security-hq-local-dynamo", "credentials")))
          .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", region.name))
          .build()
    }
  }

  def ec2Clients(configuration: Configuration, regions: List[Regions]): AwsClients[AmazonEC2Async] =
    clients(AmazonEC2AsyncClientBuilder.standard(), configuration, regions:_*)

  def cfnClients(configuration: Configuration, regions: List[Regions]): AwsClients[AmazonCloudFormationAsync] =
    clients(AmazonCloudFormationAsyncClientBuilder.standard(), configuration, regions:_*)

  // Only needs Regions.US_EAST_1
  def taClients(configuration: Configuration, region: Regions = Regions.US_EAST_1): AwsClients[AWSSupportAsync] =
    clients(AWSSupportAsyncClientBuilder.standard(), configuration, region)

  def s3Clients(configuration: Configuration, region: Regions = Regions.US_EAST_1): AwsClients[AmazonS3] =
    clients(AmazonS3ClientBuilder.standard(), configuration, region)

  def iamClients(configuration: Configuration, regions: List[Regions]): AwsClients[AmazonIdentityManagementAsync] =
    clients(AmazonIdentityManagementAsyncClientBuilder.standard(), configuration, regions:_*)

  def efsClients(configuration: Configuration, regions: List[Regions]): AwsClients[AmazonElasticFileSystemAsync] =
    clients(AmazonElasticFileSystemAsyncClientBuilder.standard(), configuration, regions:_*)
}
