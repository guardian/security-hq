package aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import config.Config
import model.{AwsAccount, DEV, PROD, Stage}
import play.api.Configuration
import utils.attempt.{Attempt, Failure}

import software.amazon.awssdk.core.client.builder.SdkClientBuilder
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.awscore.client.builder.AwsAsyncClientBuilder
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.efs.EfsAsyncClient
import software.amazon.awssdk.services.support.SupportAsyncClient



import java.util.concurrent.Executors.newCachedThreadPool

object AWS {

  def lookupAccount(accountId: String, accounts: List[AwsAccount]): Attempt[AwsAccount] = {
    Attempt.fromOption(
      accounts.find(_.id == accountId),
      Failure.awsAccountNotFound(accountId).attempt
    )
  }

  private def stsClientForRoleAssumption(account: AwsAccount): StsClient = {
    StsClient.builder.region(Config.region).credentialsProvider(ProfileCredentialsProvider.create(account.id)).build()
  }

  private def credentialsProvider(account: AwsAccount): AwsCredentialsProviderChain = {
    AwsCredentialsProviderChain.of(
      StsAssumeRoleCredentialsProvider.builder()
        .stsClient(stsClientForRoleAssumption(account))
        .refreshRequest(AssumeRoleRequest.builder.roleArn(account.roleArn).roleSessionName("security-hq").build()).build(),
      ProfileCredentialsProvider.create(account.id)
    )
  }

  private[aws] def clients[A, B <: AwsClientBuilder[B, A]](
    builder:  AwsClientBuilder[B, A],
    configuration: Configuration,
    regionList: Region*
  ): AwsClients[A] = {
    for {
      account <- Config.getAwsAccounts(configuration)
      region <- regionList
      client = builder
        .credentialsProvider(credentialsProvider(account))
        .region(region)
        .build()
    } yield AwsClient(client, account, region)
  }

  private def withCustomThreadPool[A, B <: AwsAsyncClientBuilder[B, A]] = (asyncClientBuilder: AwsAsyncClientBuilder[B, A]) =>
    asyncClientBuilder.asyncConfiguration(c => c.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, newCachedThreadPool())
  )

  def ec2Clients(configuration: Configuration, regions: List[Region]): AwsClients[Ec2AsyncClient] =
    clients(withCustomThreadPool(Ec2AsyncClient.builder), configuration, regions:_*)

  def cfnClients(configuration: Configuration, regions: List[Region]): AwsClients[CloudFormationAsyncClient] =
    clients(withCustomThreadPool(CloudFormationAsyncClient.builder), configuration, regions:_*)

  // Only needs Regions.US_EAST_1
  def taClients(configuration: Configuration, region: Region = Region.of("us-east-1")): AwsClients[SupportAsyncClient] =
    clients(withCustomThreadPool(SupportAsyncClient.builder), configuration, region)

  def s3Clients(configuration: Configuration, regions: List[Region]): AwsClients[S3Client] =
    clients(S3Client.builder, configuration, regions:_*)

  def iamClients(configuration: Configuration, regions: List[Region]): AwsClients[IamAsyncClient] =
    clients(withCustomThreadPool(IamAsyncClient.builder), configuration, regions:_*)

  def efsClients(configuration: Configuration, regions: List[Region]): AwsClients[EfsAsyncClient] =
    clients(withCustomThreadPool(EfsAsyncClient.builder), configuration, regions:_*)
}
