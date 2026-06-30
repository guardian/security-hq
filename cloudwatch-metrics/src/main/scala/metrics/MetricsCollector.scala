package metrics

import aws.ec2.EC2
import aws.iam.IAMClient
import aws.support.{TrustedAdvisorExposedIAMKeys, TrustedAdvisorS3}
import aws.{AWS, AwsClient}
import com.typesafe.scalalogging.LazyLogging
import config.AccountLoader
import logging.Cloudwatch
import model.*
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  DefaultCredentialsProvider,
  ProfileCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

/** Fetches Security HQ vulnerability data from Trusted Advisor and IAM and publishes CloudWatch metrics.
  *
  * Metrics are only published if every data set was fetched successfully so that Sum aggregations over time remain
  * meaningful. See https://github.com/guardian/security-hq/pull/211 for the rationale.
  */
object MetricsCollector extends LazyLogging {

  private given ExecutionContext = ExecutionContext.global

  private val Timeout = 5.minutes

  def run(): Unit = {
    val settings = Settings.fromEnvironment()
    logger.info(
      s"Starting cloudwatch-metrics collection (dryRun=${settings.dryRun}, region=${settings.region.id})"
    )

    val accounts = loadAccounts(settings)
    if (accounts.isEmpty) {
      logger.error(
        "No AWS accounts loaded from config; aborting metrics collection"
      )
      return
    }
    logger.info(s"Loaded ${accounts.length} AWS accounts")

    val regions = discoverRegions(settings)
    logger.info(
      s"Polling in the following regions: ${regions.map(_.id).mkString(", ")}"
    )

    val cfnClients = AWS.cfnClients(accounts, regions)
    val taClients = AWS.taClients(accounts)
    val s3Clients = AWS.s3Clients(accounts, regions)
    val iamClients = AWS.iamClients(accounts, regions)

    val startingCredentials: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] =
      accounts
        .map(a => a -> Left(Failure.notYetLoaded(a.id, "credentials").attempt))
        .toMap

    val attempt: Attempt[Unit] = for {
      exposedKeys <- TrustedAdvisorExposedIAMKeys.getAllExposedKeys(
        accounts,
        taClients
      )
      publicBuckets <- TrustedAdvisorS3.getAllPublicBuckets(
        accounts,
        taClients,
        s3Clients
      )
      credentials <- IAMClient.getAllCredentialReports(
        accounts,
        startingCredentials,
        cfnClients,
        iamClients,
        regions
      )
    } yield postMetrics(
      exposedKeys.toMap,
      publicBuckets.toMap,
      credentials.toMap,
      settings.dryRun
    )

    Await.result(attempt.asFuture, Timeout) match {
      case Right(_)      => logger.info("cloudwatch-metrics collection complete")
      case Left(failure) =>
        logger.error(
          s"cloudwatch-metrics collection failed: ${failure.logMessage}"
        )
    }
  }

  /** Only publish metrics when every data set was fetched successfully. */
  private[metrics] def postMetrics(
      exposedKeys: Map[AwsAccount, Either[FailedAttempt, List[
        ExposedIAMKeyDetail
      ]]],
      publicBuckets: Map[AwsAccount, Either[FailedAttempt, List[BucketDetail]]],
      credentials: Map[
        AwsAccount,
        Either[FailedAttempt, CredentialReportDisplay]
      ],
      dryRun: Boolean
  ): Unit = {
    val failures = collectFailures(
      List(exposedKeys, publicBuckets, credentials)
    )
    if (failures.nonEmpty) {
      logger.warn(
        s"Skipping cloudwatch metrics update as some data is missing: $failures"
      )
    } else {
      logger.info("Posting new metrics to cloudwatch")
      Cloudwatch.logAsMetric(
        exposedKeys,
        Cloudwatch.DataType.iamKeysTotal,
        dryRun
      )
      Cloudwatch.logAsMetric(publicBuckets, Cloudwatch.DataType.s3Total, dryRun)
      Cloudwatch.logMetricsForCredentialsReport(credentials, dryRun)
    }
  }

  private[metrics] def collectFailures[T](
      list: List[Map[AwsAccount, Either[FailedAttempt, T]]]
  ): List[(AwsAccount, FailedAttempt)] =
    list.flatMap { dataMap =>
      dataMap.toSeq.collect { case (account, Left(failedAttempt)) =>
        (account, failedAttempt)
      }
    }

  private def loadAccounts(settings: Settings): List[AwsAccount] = {
    val credsProvider = AwsCredentialsProviderChain.of(
      DefaultCredentialsProvider.builder.build(),
      ProfileCredentialsProvider.create("security")
    )
    val s3 = S3Client.builder.credentialsProvider(credsProvider).region(settings.region).build()
    try {
      val hocon = s3
        .getObjectAsBytes(
          GetObjectRequest.builder
            .bucket(settings.configBucket)
            .key(settings.configKey)
            .build()
        )
        .asUtf8String()
      AccountLoader.getAwsAccountsFromString(hocon)
    } finally s3.close()
  }

  private def discoverRegions(settings: Settings): List[Region] = {
    val fallback = List(settings.region, Region.US_EAST_1)
    val ec2Client = AwsClient(
      Ec2AsyncClient.builder.region(settings.region).build(),
      AwsAccount("self", "self", "self", "self"),
      settings.region
    )
    try {
      val regionsAttempt = EC2
        .getAvailableRegions(ec2Client)
        .map(_.map(r => Region.of(r.regionName)))
      Await.result(regionsAttempt.asFuture, 30.seconds) match {
        case Right(regions) if regions.nonEmpty => regions
        case _                                  => fallback
      }
    } finally ec2Client.client.close()
  }
}
