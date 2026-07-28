package aws

import config.CoreConfig
import iam.IAMClient
import model.AwsAccount
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, ProfileCredentialsProvider}
import software.amazon.awssdk.awscore
import software.amazon.awssdk.awscore.client.builder.{AwsAsyncClientBuilder, AwsClientBuilder}
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.support.SupportAsyncClient
import utils.attempt.{Attempt, Failure}

import java.util.concurrent.Executors.newCachedThreadPool
import java.util.concurrent.ConcurrentHashMap

private class AwsClientCache[T <: awscore.AwsClient](clientBuilder: (AwsAccount, Region) => T) {
  private val clients: ConcurrentHashMap[(AwsAccount, String), AwsClient[T]] = new ConcurrentHashMap()

  def getClient(account: AwsAccount, region: Region): AwsClient[T] =
    clients.computeIfAbsent((account, region.id), _ => AwsClient(clientBuilder(account, region), account, region))

  def getClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[T] =
    for {
      account <- accounts
      region <- regions
    } yield getClient(account, region)
}

object AWS {

  def lookupAccount(accountId: String, accounts: List[AwsAccount]): Attempt[AwsAccount] = {
    Attempt.fromOption(
      accounts.find(_.id == accountId),
      Failure.awsAccountNotFound(accountId).attempt
    )
  }

  private def credentialsProvider(account: AwsAccount): AwsCredentialsProviderChain = {
    AwsCredentialsProviderChain.of(
      StsAssumeRoleCredentialsProvider
        .builder()
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

  private def client[A, B <: AwsClientBuilder[B, A]](
      clientBuilder: AwsClientBuilder[B, A],
      account: AwsAccount,
      region: Region
  ): A =
    clientBuilder
      .credentialsProvider(credentialsProvider(account))
      .region(region)
      .build()

  private lazy val sharedThreadPool = newCachedThreadPool()

  private def withCustomThreadPool[A, B <: AwsAsyncClientBuilder[B, A]](
      asyncClientBuilder: AwsAsyncClientBuilder[B, A]
  ): B = asyncClientBuilder.asyncConfiguration(c =>
    c.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, sharedThreadPool)
  )

  private def cfnClientBuilder(account: AwsAccount, region: Region): CloudFormationAsyncClient =
    client(withCustomThreadPool(CloudFormationAsyncClient.builder()), account, region)
  private val cfnClientCache = new AwsClientCache(cfnClientBuilder)

  def cfnClient(account: AwsAccount, region: Region): AwsClient[CloudFormationAsyncClient] =
    cfnClientCache.getClient(account, region)

  def cfnClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[CloudFormationAsyncClient] =
    cfnClientCache.getClients(accounts, regions)

  def cfnClients(regionsByAccount: Map[AwsAccount, List[Region]]): AwsClients[CloudFormationAsyncClient] =
    regionsByAccount.toList.flatMap { case (account, regions) =>
      regions.map(region => cfnClient(account, region))
    }

  private def ec2ClientBuilder(account: AwsAccount, region: Region): Ec2AsyncClient =
    client(withCustomThreadPool(Ec2AsyncClient.builder()), account, region)
  private val ec2ClientCache = new AwsClientCache(ec2ClientBuilder)

  def ec2Client(account: AwsAccount, region: Region): AwsClient[Ec2AsyncClient] =
    ec2ClientCache.getClient(account, region)

  private def taClientBuilder(account: AwsAccount, region: Region): SupportAsyncClient =
    client(withCustomThreadPool(SupportAsyncClient.builder), account, region)
  private val taClientCache = new AwsClientCache(taClientBuilder)

  // Only needs Regions.US_EAST_1
  def taClients(accounts: List[AwsAccount], region: Region = Region.of("us-east-1")): AwsClients[SupportAsyncClient] =
    taClientCache.getClients(accounts, List(region))

  private def s3ClientBuilder(account: AwsAccount, region: Region): S3Client =
    client(S3Client.builder(), account, region)
  private val s3ClientCache = new AwsClientCache(s3ClientBuilder)

  def s3Client(account: AwsAccount, region: Region): AwsClient[S3Client] =
    s3ClientCache.getClient(account, region)

  def s3Clients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[S3Client] =
    s3ClientCache.getClients(accounts, regions)

  private def iamClientBuilder(account: AwsAccount, region: Region): IamAsyncClient =
    client(withCustomThreadPool(IamAsyncClient.builder()), account, region)
  private val iamClientCache = new AwsClientCache(iamClientBuilder)

  // Only needs Regions.US_EAST_1
  def iamClient(account: AwsAccount): AwsClient[IamAsyncClient] =
    iamClientCache.getClient(account, IAMClient.SOLE_REGION)

  def iamClients(accounts: List[AwsAccount]): AwsClients[IamAsyncClient] =
    iamClientCache.getClients(accounts, List(IAMClient.SOLE_REGION))
}
