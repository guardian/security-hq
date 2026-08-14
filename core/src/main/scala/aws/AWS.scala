package aws

import config.CoreConfig
import iam.IAMClient
import model.AwsAccount
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, ProfileCredentialsProvider}
import software.amazon.awssdk.awscore
import software.amazon.awssdk.awscore.client.builder.{AwsAsyncClientBuilder, AwsClientBuilder}
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest

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

  private def iamClientBuilder(account: AwsAccount, region: Region): IamAsyncClient =
    client(withCustomThreadPool(IamAsyncClient.builder()), account, region)
  private val iamClientCache = new AwsClientCache(iamClientBuilder)

  def iamClients(accounts: List[AwsAccount]): AwsClients[IamAsyncClient] =
    iamClientCache.getClients(accounts, List(IAMClient.SOLE_REGION))
}
