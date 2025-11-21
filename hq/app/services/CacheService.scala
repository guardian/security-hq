package services

import aws.AwsClients
import aws.iam.IAMClient
import aws.support.{TrustedAdvisorExposedIAMKeys, TrustedAdvisorS3}
import config.Config
import model.*
import org.apache.pekko.actor.ActorSystem
import play.api.*
import play.api.inject.ApplicationLifecycle
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import org.joda.time.DateTime
import utils.Box
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.support.SupportAsyncClient


class CacheService(
    config: Configuration,
    lifecycle: ApplicationLifecycle,
    environment: Environment,
    cfnClients: AwsClients[CloudFormationAsyncClient],
    taClients: AwsClients[SupportAsyncClient],
    s3Clients: AwsClients[S3Client],
    iamClients: AwsClients[IamAsyncClient],
    regions: List[Region]
)(implicit ec: ExecutionContext, actorSystem: ActorSystem)
    extends Logging {
  private val accounts = Config.getAwsAccounts(config)
  private def startingCache(cacheContent: String) = {
    accounts
      .map(acc =>
        (acc, Left(Failure.notYetLoaded(acc.id, cacheContent).attempt))
      )
      .toMap
  }
  private val publicBucketsBox
      : Box[Map[AwsAccount, Either[FailedAttempt, List[BucketDetail]]]] = Box(
    startingCache("public buckets")
  )
  private val credentialsBox
      : Box[Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]] =
    Box(startingCache("credentials"))
  private val exposedKeysBox
      : Box[Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]]] =
    Box(startingCache("exposed keys"))

  def getAllPublicBuckets
      : Map[AwsAccount, Either[FailedAttempt, List[BucketDetail]]] =
    publicBucketsBox.get()

  def getPublicBucketsForAccount(
      awsAccount: AwsAccount
  ): Either[FailedAttempt, List[BucketDetail]] = {
    publicBucketsBox
      .get()
      .getOrElse(
        awsAccount,
        Left(
          Failure
            .cacheServiceErrorPerAccount(awsAccount.id, "public buckets")
            .attempt
        )
      )
  }

  def getAllCredentials
      : Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] =
    credentialsBox.get()

  def getCredentialsForAccount(
      awsAccount: AwsAccount
  ): Either[FailedAttempt, CredentialReportDisplay] = {
    credentialsBox
      .get()
      .getOrElse(
        awsAccount,
        Left(
          Failure
            .cacheServiceErrorPerAccount(awsAccount.id, "credentials")
            .attempt
        )
      )
  }

  def getAllExposedKeys
      : Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]] =
    exposedKeysBox.get()

  def getExposedKeysForAccount(
      awsAccount: AwsAccount
  ): Either[FailedAttempt, List[ExposedIAMKeyDetail]] = {
    exposedKeysBox
      .get()
      .getOrElse(
        awsAccount,
        Left(
          Failure
            .cacheServiceErrorPerAccount(awsAccount.id, "exposed keys")
            .attempt
        )
      )
  }

  def refreshCredentialsBox(): Unit = {
    logger.info("Started refresh of the Credentials data")
    for {
      updatedCredentialReports <- IAMClient.getAllCredentialReports(
        accounts,
        credentialsBox.get(),
        cfnClients,
        iamClients,
        regions
      )
    } yield {
      logCacheDataStatus("Credentials", updatedCredentialReports)
      credentialsBox.send(updatedCredentialReports.toMap)
    }
  }

  private def refreshPublicBucketsBox(): Unit = {
    logger.info("Started refresh of the public S3 buckets data")
    for {
      allPublicBuckets <- TrustedAdvisorS3.getAllPublicBuckets(
        accounts,
        taClients,
        s3Clients
      )
    } yield {
      logCacheDataStatus("Public buckets", allPublicBuckets)
      publicBucketsBox.send(allPublicBuckets.toMap)
    }
  }

  private def refreshExposedKeysBox(): Unit = {
    logger.info("Started refresh of the Exposed Keys data")
    for {
      allExposedKeys <- TrustedAdvisorExposedIAMKeys.getAllExposedKeys(
        accounts,
        taClients
      )
    } yield {
      logCacheDataStatus("Exposed Keys", allExposedKeys)
      exposedKeysBox.send(allExposedKeys.toMap)
    }
  }

  if (environment.mode != Mode.Test) {
    val initialDelay =
      if (environment.mode == Mode.Prod) 10.seconds
      else Duration.Zero

    val publicBucketsSubscription = {
      actorSystem.scheduler.scheduleAtFixedRate(
        initialDelay = initialDelay + 1000.millis,
        interval = 5.minutes
      ){ () =>
        refreshPublicBucketsBox()        
      }
    }

    val exposedKeysSubscription = {
      actorSystem.scheduler.scheduleAtFixedRate(
        initialDelay = initialDelay + 2000.millis,
        interval = 5.minutes
      ){ () =>
        refreshExposedKeysBox()
      }
    }

    val credentialsSubscription = {
      actorSystem.scheduler.scheduleAtFixedRate(
        initialDelay = initialDelay + 4000.millis,
        interval = 5.minutes
      ){ () =>
        refreshCredentialsBox()
      }
    }

    lifecycle.addStopHook { () =>
      publicBucketsSubscription.cancel()
      exposedKeysSubscription.cancel()
      credentialsSubscription.cancel()
      Future.successful(())
    }
  }

  /**
   * Prints an overview of this cache data.
   *
   * If everything succeeded then we say as much. If the cache data contains failures
   * we log a warning that shows which accounts are affected and give one failure
   * as the underlying cause, if available.
   */
  def logCacheDataStatus[A](cacheName: String, data: Seq[(AwsAccount, Either[FailedAttempt, A])]): Unit = {
    val (successful, failed) = data.partition { case (_, result) => result.isRight }

    if (failed.isEmpty) {
      logger.info(s"$cacheName updated: All ${data.size} accounts successful")
    } else {
      val failedAccountsDetails = failed.flatMap {
        case (account, Left(failedAttempt)) =>
          Some(s"${account.name}: ${failedAttempt.logMessage}")
        case _ => None
      }.mkString(", ")
      val logMessage = s"$cacheName updated: ${successful.size}/${data.size} accounts succeeded. Failed accounts: $failedAccountsDetails"
      failed.flatMap {
        case (_, Left(failedAttempt)) =>
          failedAttempt.firstException
        case _ => None
      }.headOption match {
        case None =>
          logger.warn(logMessage)
        case Some(exampleCausedBy) =>
          logger.warn(s"$logMessage - see stacktrace for an example cause", exampleCausedBy)
      }
    }
  }
}
