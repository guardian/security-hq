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

/** Removes AWS access for colleagues who have departed.
  *
  * This is targeted at "recovery access", where teams keep one or two IAM users that can be used to gain access to AWS
  * when Janus is down. These recovery users have a password (and MFA) but no long-lived credentials, and should also be
  * tagged with the Google username of the individual so we can identify them.
  *
  * We load the Guardian's Janus configuration and decide who is 'recognised' by comparing it with those Google identity
  * tags. If an IAM user is tagged with an identity that is no longer in Janus, we assume they have left and deactivate
  * the user — removing their access keys and login profile, and notifying the relevant team via Anghammarad.
  */
object UnrecognisedUsers extends LazyLogging {

  private val ALLOWED_ACCOUNT_IDS = "ALLOWED_ACCOUNT_IDS"
  private val ANGHAMMARAD_SNS_ARN = "ANGHAMMARAD_SNS_TOPIC_ARN"

  def run(
      env: Map[String, String] = sys.env,
      restrictToAccountId: Option[String] = None,
      timeout: FiniteDuration = 5.minutes
  ): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val settings = Settings.fromEnvironment(env)
    logger.info(s"Starting iam-unrecognised-users job (dryRun=${settings.dryRun}, region=${settings.region.id})")
    val result = disableUnrecognisedUsers(settings, restrictToAccountId)
    Await.result(result.asFuture, timeout) match {
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

    // `getAllCredentialReports` refreshes an existing per-account report map. There is no previous report to build
    // on, so seed every account as "not yet loaded" to force a fresh report to be fetched for each.
    def unloadedReport(account: AwsAccount): Either[FailedAttempt, CredentialReportDisplay] =
      Left(Failure.notYetLoaded(account.id, "credentials").attempt)

    for {
      // load Security HQ's config (accounts, allowed accounts, notification topic) from S3
      configSource <- getS3Object(s3Client, settings.configBucket, settings.configKey)
      conf = ConfigFactory.parseString(configSource.mkString)
      allAccounts = AccountLoader.getAwsAccounts(conf)
      awsAccounts = restrictToAccountId.fold(allAccounts)(id => allAccounts.filter(_.id == id))
      allowedAccountIds = conf.getStringList(ALLOWED_ACCOUNT_IDS).asScala.toList
      anghammaradSnsArn = conf.getString(ANGHAMMARAD_SNS_ARN)
      iamClients = AWS.iamClients(awsAccounts, regions)
      cfnClients = AWS.cfnClients(awsAccounts, regions)
      startingData = awsAccounts.map(account => account -> unloadedReport(account)).toMap
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
      // deactivate access keys and remove login profiles for unrecognised users (skipped in dry run)
      _ <- Attempt.traverse(accessKeys)(disableAccountAccessKeys(_, iamClients, settings.dryRun)).map(_ => ())
      _ <- Attempt.traverse(unrecognisedUsers)(removeAccountPasswords(_, iamClients, settings.dryRun)).map(_ => ())
      // construct and send a notification for each unrecognised user
      notifications = unrecognisedUserNotifications(unrecognisedUsers, settings.dryRun)
      // if in dry run, notifications list is empty
      notificationIds <- Attempt.traverse(notifications)(AnghammaradNotifications.send(_, anghammaradSnsArn, snsClient))
    } yield notificationIds
  }
}
