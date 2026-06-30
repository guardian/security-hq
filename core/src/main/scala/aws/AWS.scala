package aws

import config.CoreConfig
import model.AwsAccount
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, ProfileCredentialsProvider}
import software.amazon.awssdk.awscore.client.builder.{AwsAsyncClientBuilder, AwsClientBuilder}
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.efs.EfsAsyncClient
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.support.SupportAsyncClient
import utils.attempt.{Attempt, Failure}

import java.util.concurrent.Executors.newCachedThreadPool

object AWS {

  def lookupAccount(accountId: String, accounts: List[AwsAccount]): Attempt[AwsAccount] = {
    Attempt.fromOption(
      accounts.find(_.id == accountId),
      Failure.awsAccountNotFound(accountId).attempt
    )
  }

  private def credentialsProvider(account: AwsAccount): AwsCredentialsProviderChain = {
    AwsCredentialsProviderChain.of(
      StsAssumeRoleCredentialsProvider.builder()
        .stsClient(
          StsClient.builder
            .region(CoreConfig.region)
            .build()
        )
        .refreshRequest(
          AssumeRoleRequest.builder
            .roleArn(account.roleArn)
            .roleSessionName("security-hq")
            .build()
        )
        .build(),
      ProfileCredentialsProvider.create(account.id)
    )
  }

  private[aws] def clients[A, B <: AwsClientBuilder[B, A]](
    builder:  AwsClientBuilder[B, A],
    accounts: List[AwsAccount],
    regionList: Region*
  ): AwsClients[A] = {
    for {
      account <- accounts
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

  def ec2Clients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[Ec2AsyncClient] =
    clients(withCustomThreadPool(Ec2AsyncClient.builder), accounts, regions:_*)

  def cfnClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[CloudFormationAsyncClient] =
    clients(withCustomThreadPool(CloudFormationAsyncClient.builder), accounts, regions:_*)

  // Only needs Regions.US_EAST_1
  def taClients(accounts: List[AwsAccount], region: Region = Region.of("us-east-1")): AwsClients[SupportAsyncClient] =
    clients(withCustomThreadPool(SupportAsyncClient.builder), accounts, region)

  def s3Clients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[S3Client] =
    clients(S3Client.builder, accounts, regions:_*)

  def iamClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[IamAsyncClient] =
    clients(withCustomThreadPool(IamAsyncClient.builder), accounts, regions:_*)

  def efsClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[EfsAsyncClient] =
    clients(withCustomThreadPool(EfsAsyncClient.builder), accounts, regions:_*)
}
