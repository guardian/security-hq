package unrecognised

import aws.AWS
import aws.iam.IAMClient
import aws.s3.S3.getS3Object
import com.gu.janus.JanusConfig
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import config.AccountLoader
import logic.IamUnrecognisedUsers.*
import model.{AwsAccount, CredentialReportDisplay}
import notifications.AnghammaradNotifications
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsAsyncClient
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters.*

/** Identifies IAM users tagged with a Google username that is no longer present in Janus (the canonical source of
  * "recognised" users) and disables their access keys and passwords, notifying the relevant teams via Anghammarad.
  *
  * This is the standalone-lambda equivalent of the job that previously ran on a scheduler inside the Play app
  * (`IamRemediationService.disableUnrecognisedUsers`). Rather than reading an in-memory cache, the lambda fetches the
  * IAM credential reports directly for every configured account.
  */
object UnrecognisedUsers extends LazyLogging {

  // Keys within `security-hq.conf` (loaded from S3); `AWS_ACCOUNTS` is parsed by the shared `AccountLoader`.
  private val ALLOWED_ACCOUNT_IDS = "ALLOWED_ACCOUNT_IDS"
  private val ANGHAMMARAD_SNS_ARN = "ANGHAMMARAD_SNS_TOPIC_ARN"

  def run(env: Map[String, String] = sys.env, restrictToAccountId: Option[String] = None): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val settings = Settings.fromEnvironment(env)
    logger.info(s"Starting iam-unrecognised-users job (dryRun=${settings.dryRun}, region=${settings.region.id})")
    val result = disableUnrecognisedUsers(settings, restrictToAccountId)
    Await.result(result.asFuture, 5.minutes) match {
      case Left(failure) =>
        logger.error(s"Failed to run unrecognised user job: ${failure.logMessage}")
        throw new RuntimeException(failure.logMessage)
      case Right(notificationIds) =>
        logger.info(s"Successfully ran unrecognised user job and sent ${notificationIds.length} notification(s).")
    }
  }

  def disableUnrecognisedUsers(
      settings: Settings,
      restrictToAccountId: Option[String] = None
  )(implicit ec: ExecutionContext): Attempt[List[String]] = {
    val s3Client = S3Client.builder.region(settings.region).build()
    val snsClient = SnsAsyncClient.builder.region(settings.region).build()
    // IAM is global; us-east-1 is required for credential reports. Additional regions only affect CloudFormation stack
    // enrichment, which this job does not rely on.
    val regions = List(settings.region, Region.of("us-east-1")).distinct

    for {
      // load the app's config (accounts, allowed accounts, notification topic) from S3
      configSource <- getS3Object(s3Client, settings.configBucket, settings.configKey)
      conf = ConfigFactory.parseString(configSource.mkString)
      allAccounts = AccountLoader.getAwsAccounts(conf)
      awsAccounts = restrictToAccountId.fold(allAccounts)(id => allAccounts.filter(_.id == id))
      allowedAccountIds = conf.getStringList(ALLOWED_ACCOUNT_IDS).asScala.toList
      anghammaradSnsArn = conf.getString(ANGHAMMARAD_SNS_ARN)
      iamClients = AWS.iamClients(awsAccounts, regions)
      cfnClients = AWS.cfnClients(awsAccounts, regions)
      startingData: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] =
        awsAccounts.map { account =>
          account -> (Left(Failure.notYetLoaded(account.id, "credentials").attempt): Either[FailedAttempt, CredentialReportDisplay])
        }.toMap
      // fetch and parse our stored Janus config to use as the canonical source of "recognised" usernames
      janusSource <- getS3Object(s3Client, settings.janusBucket, settings.janusKey)
      janusData = JanusConfig.load(makeFile(janusSource.mkString))
      janusUsernames = getJanusUsernames(janusData)
      // generate a fresh IAM credential report for every configured account
      credentialReports <- IAMClient.getAllCredentialReports(awsAccounts, startingData, cfnClients, iamClients, regions)
      accountCredsReports = getCredsReportDisplayForAccount(credentialReports.toMap)
      // determine the unrecognised users by comparing Janus usernames to the IAM users (filtered to allowed accounts)
      unrecognisedUsers = unrecognisedUsersForAllowedAccounts(accountCredsReports, janusUsernames, allowedAccountIds)
      accessKeys <- Attempt.traverse(unrecognisedUsers)(listAccountAccessKeys(_, iamClients))
      // disable each access key and remove each login profile for unrecognised users (unless this is a dry run)
      _ <-
        if (settings.dryRun) {
          logger.info(s"Dry run: would disable ${accessKeys.flatMap(_.vulnerableAccessKey).length} access key(s).")
          Attempt.Right(())
        } else Attempt.traverse(accessKeys)(disableAccountAccessKeys(_, iamClients)).map(_ => ())
      _ <-
        if (settings.dryRun) Attempt.Right(())
        else Attempt.traverse(unrecognisedUsers)(removeAccountPasswords(_, iamClients)).map(_ => ())
      // construct and send a notification for each unrecognised user
      notifications = unrecognisedUserNotifications(unrecognisedUsers)
      notificationIds <-
        if (settings.dryRun) {
          logger.info(s"Dry run: would send ${notifications.length} notification(s).")
          Attempt.Right(List.empty[String])
        } else Attempt.traverse(notifications)(AnghammaradNotifications.send(_, anghammaradSnsArn, snsClient))
    } yield notificationIds
  }
}
