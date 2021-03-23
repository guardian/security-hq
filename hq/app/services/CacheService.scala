package services

import api.Snyk
import aws.AwsClients
import aws.ec2.EC2
import aws.iam.IAMClient
import aws.support.{TrustedAdvisorExposedIAMKeys, TrustedAdvisorS3}
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.elasticfilesystem.AmazonElasticFileSystemAsync
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.support.AWSSupportAsync
import com.google.cloud.securitycenter.v1.{OrganizationName, SecurityCenterClient}
import com.gu.Box
import config.Config
import logic.GcpDisplay
import model._
import play.api._
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import rx.lang.scala.Observable
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import logging.Cloudwatch


class CacheService(
    config: Configuration,
    lifecycle: ApplicationLifecycle,
    environment: Environment,
    configraun: com.gu.configraun.models.Configuration,
    wsClient: WSClient,
    ec2Clients: AwsClients[AmazonEC2Async],
    cfnClients: AwsClients[AmazonCloudFormationAsync],
    taClients: AwsClients[AWSSupportAsync],
    s3Clients: AwsClients[AmazonS3],
    iamClients: AwsClients[AmazonIdentityManagementAsync],
    efsClients: AwsClients[AmazonElasticFileSystemAsync],
    regions: List[Regions],
    gcpClient: SecurityCenterClient
  )(implicit ec: ExecutionContext) extends Logging {
  private val accounts = Config.getAwsAccounts(config)
  private val startingCache = accounts.map(acc => (acc, Left(Failure.cacheServiceErrorPerAccount(acc.id, "cache").attempt))).toMap
  private val publicBucketsBox: Box[Map[AwsAccount, Either[FailedAttempt, List[BucketDetail]]]] = Box(startingCache)
  private val credentialsBox: Box[Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]] = Box(startingCache)
  private val exposedKeysBox: Box[Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]]] = Box(startingCache)
  private val sgsBox: Box[Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]]] = Box(startingCache)
  private val snykBox: Box[Attempt[List[SnykProjectIssues]]] = Box(Attempt.fromEither(Left(Failure.cacheServiceErrorAllAccounts("cache").attempt)))
  private val gcpBox: Box[Attempt[List[GcpFinding]]] = Box(Attempt.fromEither(Left(Failure.cacheServiceErrorGcp("GCP").attempt)))

  def getAllPublicBuckets: Map[AwsAccount, Either[FailedAttempt, List[BucketDetail]]] = publicBucketsBox.get()

  def getPublicBucketsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[BucketDetail]] = {
    publicBucketsBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceErrorPerAccount(awsAccount.id, "public buckets").attempt)
    )
  }

  def getAllCredentials: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = credentialsBox.get()

  def getCredentialsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, CredentialReportDisplay] = {
    credentialsBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceErrorPerAccount(awsAccount.id, "credentials").attempt)
    )
  }

  def getAllExposedKeys: Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]] = exposedKeysBox.get()

  def getExposedKeysForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[ExposedIAMKeyDetail]] = {
    exposedKeysBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceErrorPerAccount(awsAccount.id, "exposed keys").attempt)
    )
  }

  def getAllSgs: Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]] = sgsBox.get()

  def getSgsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]] = {
    sgsBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceErrorPerAccount(awsAccount.id, "security group").attempt)
    )
  }

  def getAllSnykResults: Attempt[List[SnykProjectIssues]] = snykBox.get()

  def getGcpFindings: Attempt[List[GcpFinding]] = gcpBox.get()

  def refreshCredentialsBox(): Unit = {
    logger.info("Started refresh of the Credentials data")
    for {
      allCredentialReports <- IAMClient.getAllCredentialReports(accounts, cfnClients, iamClients, regions)
    } yield {
      logger.info("Sending the refreshed data to the Credentials Box")
      credentialsBox.send(allCredentialReports.toMap)
    }
  }

  private def refreshPublicBucketsBox(): Unit = {
    logger.info("Started refresh of the public S3 buckets data")
    for {
      allPublicBuckets <- TrustedAdvisorS3.getAllPublicBuckets(accounts, taClients, s3Clients)
    } yield {
      logger.info("Sending the refreshed data to the Public Buckets Box")
      if(allPublicBuckets.exists(_._2.isRight)) {
        publicBucketsBox.send(allPublicBuckets.toMap)
      }
    }
  }

  private def refreshExposedKeysBox(): Unit = {
    logger.info("Started refresh of the Exposed Keys data")
    for {
      allExposedKeys <- TrustedAdvisorExposedIAMKeys.getAllExposedKeys(accounts, taClients)
    } yield {
      logger.info("Sending the refreshed data to the Exposed Keys Box")
      exposedKeysBox.send(allExposedKeys.toMap)
    }
  }

  private def refreshSgsBox(): Unit = {
    logger.info("Started refresh of the Security Groups data")
    for {
      _ <- EC2.refreshSGSReports(accounts, taClients)
      allFlaggedSgs <- EC2.allFlaggedSgs(accounts, ec2Clients, efsClients, taClients)
    } yield {
      logger.info("Sending the refreshed data to the Security Groups Box")
      sgsBox.send(allFlaggedSgs.toMap)
    }
  }

  def refreshSnykBox(): Unit = {
    logger.info("Started refresh of the Snyk data")
    for {
      allSnykRuns <- Snyk.allSnykRuns(configraun, wsClient)
    } yield {
      logger.info("Sending the refreshed data to the Snyk Box")
      snykBox.send(Attempt.Right(allSnykRuns))
    }
  }

  def refreshGcpBox(): Unit = {
    logger.info("Started refresh of GCP data")
    val organisation = OrganizationName.of(Config.gcpSccAuthentication(config).orgId)
    for {
      gcpFindings <- GcpDisplay.getGcpFindings(organisation, gcpClient, config)
    } yield {
      logger.info("Sending the refreshed data to the GCP Box")
      gcpBox.send(Attempt.Right(gcpFindings))
    }
  }

  if (environment.mode != Mode.Test) {
    val initialDelay =
      if (environment.mode == Mode.Prod) 10.seconds
      else Duration.Zero

    val publicBucketsSubscription = Observable.interval(initialDelay + 1000.millis, 5.minutes).subscribe { _ =>
      refreshPublicBucketsBox()
    }

    val exposedKeysSubscription = Observable.interval(initialDelay + 2000.millis, 5.minutes).subscribe { _ =>
      refreshExposedKeysBox()
    }

    val sgSubscription = Observable.interval(initialDelay + 3000.millis, 5.minutes).subscribe { _ =>
      refreshSgsBox()
    }

    val credentialsSubscription = Observable.interval(initialDelay + 4000.millis, 90.minutes).subscribe { _ =>
      refreshCredentialsBox()
    }

    val snykSubscription = Observable.interval(initialDelay + 6000.millis, 30.minutes).subscribe { _ =>
      refreshSnykBox()
    }

    val gcpSubscription = Observable.interval(initialDelay + 6000.millis, 90.minutes).subscribe { _ =>
      logger.info("refreshing the GCP Box now")
      refreshGcpBox()
    }

    lifecycle.addStopHook { () =>
      publicBucketsSubscription.unsubscribe()
      exposedKeysSubscription.unsubscribe()
      sgSubscription.unsubscribe()
      credentialsSubscription.unsubscribe()
      snykSubscription.unsubscribe()
      gcpSubscription.unsubscribe()
      Future.successful(())
    }
  }
}
