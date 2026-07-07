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

import java.util.concurrent.{ExecutorService, Executors}

object AWS {

  def lookupAccount(accountId: String, accounts: List[AwsAccount]): Attempt[AwsAccount] = {
    Attempt.fromOption(
      accounts.find(_.id == accountId),
      Failure.awsAccountNotFound(accountId).attempt
    )
  }

  private def credentialsProvider(account: AwsAccount): ClientCredentials = {
    // Built explicitly (rather than via `.stsClient(...)`'s default lookup) so that we retain a handle on it and can
    // close it alongside the provider chain; the STS provider does not take ownership of a client supplied this way.
    val stsClient = StsClient.builder.region(CoreConfig.region).build()
    val provider = AwsCredentialsProviderChain.of(
      StsAssumeRoleCredentialsProvider
        .builder()
        .stsClient(stsClient)
        .refreshRequest(
          AssumeRoleRequest.builder
            .roleArn(account.roleArn)
            .roleSessionName("security-hq")
            .build()
        )
        .build(),
      ProfileCredentialsProvider.create(account.id)
    )
    ClientCredentials(provider, stsClient)
  }

  /** @param newBuilder
    *   a factory for a fresh builder, called once per account/region combination, so that each resulting client is
    *   entirely independent of the others (and can therefore be closed independently too).
    */
  private[aws] def clients[A, B <: AwsClientBuilder[B, A]](
      newBuilder: () => AwsClientBuilder[B, A],
      accounts: List[AwsAccount],
      regionList: Region*
  ): AwsClients[A] = {
    for {
      account <- accounts
      region <- regionList
      creds = credentialsProvider(account)
      client = newBuilder()
        .credentialsProvider(creds.provider)
        .region(region)
        .build()
    } yield AwsClient(client, account, region, Some(creds))
  }

  /** As [[clients]], but for async clients: each client is additionally given its own dedicated executor to back its
    * async completions (rather than one executor shared across every account/region), so that the executor can be
    * shut down deterministically alongside the client that owns it. See [[ClientExecutor]] for why a dedicated
    * executor per client, rather than one shared pool, matters for prompt shutdown.
    *
    * The full builder chain (including `.asyncConfiguration(...)`, `.credentialsProvider(...)` and `.region(...)`)
    * is left entirely to the caller, rather than factored out generically in here: `AwsAsyncClientBuilder` and
    * `AwsClientBuilder` are separate SDK interfaces that concrete builders (for example `Ec2AsyncClientBuilder`)
    * happen to implement together, but chaining their methods through an abstract type parameter bounded by either
    * interface alone does not see the other's members. Building the whole client from a concrete builder type avoids
    * that problem.
    */
  private[aws] def asyncClients[A](
      newClient: (AwsCredentialsProviderChain, Region, ExecutorService) => A,
      accounts: List[AwsAccount],
      regionList: Region*
  ): AwsClients[A] = {
    for {
      account <- accounts
      region <- regionList
      creds = credentialsProvider(account)
      executor = Executors.newCachedThreadPool()
      client = newClient(creds.provider, region, executor)
    } yield AwsClient(client, account, region, Some(creds), Some(ClientExecutor(executor)))
  }

  private def withExecutor[A, B <: AwsAsyncClientBuilder[B, A]](builder: AwsAsyncClientBuilder[B, A], executor: ExecutorService) =
    builder.asyncConfiguration(c => c.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, executor))

  def ec2Clients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[Ec2AsyncClient] =
    asyncClients(
      (creds, region, executor) =>
        withExecutor(Ec2AsyncClient.builder, executor).credentialsProvider(creds).region(region).build(),
      accounts,
      regions: _*
    )

  def cfnClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[CloudFormationAsyncClient] =
    asyncClients(
      (creds, region, executor) =>
        withExecutor(CloudFormationAsyncClient.builder, executor).credentialsProvider(creds).region(region).build(),
      accounts,
      regions: _*
    )

  // Only needs Regions.US_EAST_1
  def taClients(accounts: List[AwsAccount], region: Region = Region.of("us-east-1")): AwsClients[SupportAsyncClient] =
    asyncClients(
      (creds, region, executor) =>
        withExecutor(SupportAsyncClient.builder, executor).credentialsProvider(creds).region(region).build(),
      accounts,
      region
    )

  def s3Clients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[S3Client] =
    clients(() => S3Client.builder, accounts, regions: _*)

  def iamClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[IamAsyncClient] =
    asyncClients(
      (creds, region, executor) =>
        withExecutor(IamAsyncClient.builder, executor).credentialsProvider(creds).region(region).build(),
      accounts,
      regions: _*
    )

  def efsClients(accounts: List[AwsAccount], regions: List[Region]): AwsClients[EfsAsyncClient] =
    asyncClients(
      (creds, region, executor) =>
        withExecutor(EfsAsyncClient.builder, executor).credentialsProvider(creds).region(region).build(),
      accounts,
      regions: _*
    )
}
