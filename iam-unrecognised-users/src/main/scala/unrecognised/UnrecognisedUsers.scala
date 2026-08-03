package unrecognised

import aws.iam.IAMClient
import aws.iam.IAMClient.getAllCredentialReports
import aws.s3.S3.getS3Object
import aws.{AWS, AwsClients}
import com.gu.janus.JanusConfig
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import config.CoreConfig
import logging.Cloudwatch
import logging.Cloudwatch.ReaperExecutionStatus
import logic.IamUnrecognisedUsers.*
import model.*
import notifications.AnghammaradNotifications
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.{DeleteLoginProfileResponse, UpdateAccessKeyResponse}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsAsyncClient
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

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
      timeout: FiniteDuration = 5.minutes
  )(using ExecutionContext): Unit = {
    val settings = Settings.fromEnvironment(env)
    logger.info(s"Starting iam-unrecognised-users job (dryRun=${settings.dryRun}, region=${settings.region.id})")
    val result = disableUnrecognisedUsers(settings)
    Await.result(result.asFuture, timeout) match {
      case Left(failure) =>
        logger.error(s"Failed to run unrecognised user job: ${failure.logMessage}")
        throw new RuntimeException(failure.logMessage)
      case Right(notificationIds) =>
        logger.info(s"Successfully ran unrecognised user job and sent ${notificationIds.length} notification(s).")
    }
  }

  private[unrecognised] def disableUnrecognisedUsers(
      settings: Settings
  )(using ExecutionContext): Attempt[List[String]] = {
    val s3Client = S3Client.builder
      .region(settings.region)
      .credentialsProvider(CoreConfig.securityCredentialsProvider)
      .build()
    lazy val snsClient = SnsAsyncClient.builder.region(settings.region).build()

    // `getAllCredentialReports` refreshes an existing per-account report map. There is no previous report to build
    // on, so seed every account as "not yet loaded" to force a fresh report to be fetched for each.
    def unloadedReport(account: AwsAccount): Either[FailedAttempt, CredentialReportDisplay] =
      Left(Failure.notYetLoaded(account.id, "credentials").attempt)

    for {
      // load Security HQ's config (accounts, allowed accounts, notification topic) from S3
      configSource <- getS3Object(s3Client, settings.configBucket, settings.configKey)
      conf = ConfigFactory.parseString(configSource.mkString)
      awsAccounts = CoreConfig.parseAccounts(conf)
      allowedAccountIds = conf.getStringList(ALLOWED_ACCOUNT_IDS).asScala.toList
      anghammaradSnsArn = conf.getString(ANGHAMMARAD_SNS_ARN)
      iamClients = AWS.iamClients(awsAccounts)
      startingData = awsAccounts.map(account => account -> unloadedReport(account)).toMap
      // fetch and parse our stored Janus config to use as the canonical source of "recognised" usernames
      janusSource <- getS3Object(s3Client, settings.janusBucket, settings.janusKey)
      janusData = JanusConfig.load(makeFile(janusSource.mkString))
      janusUsernames = getJanusUsernames(janusData)
      // generate a fresh IAM credential report for every configured account, logging the per-account outcome
      credentialReports <- getAllCredentialReports(awsAccounts, startingData, iamClients)
        .map(_.tap(logCredentialReportResults))
      accountCredsReports = getCredsReportDisplayForAccount(credentialReports.toMap)
      // determine the unrecognised users by comparing Janus usernames to the IAM users (filtered to allowed accounts)
      unrecognisedUsers = unrecognisedUsersForAllowedAccounts(accountCredsReports, janusUsernames, allowedAccountIds)
      accessKeys <- Attempt.traverse(unrecognisedUsers)(listAccountAccessKeys(_, iamClients))
      // deactivate access keys and remove login profiles for unrecognised users (skipped in dry run)
      // First emit a zero metric in case there's no keys to disable
      _ = Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.success, 0)
      _ <- Attempt.traverse(accessKeys)(disableAccountAccessKeys(_, iamClients, settings.dryRun))
      // First emit a zero metric in case there's no passwords to remove
      _ = Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.success, 0)
      _ <- Attempt.traverse(unrecognisedUsers)(removeAccountPasswords(_, iamClients, settings.dryRun))
      // construct and send a notification for each unrecognised user
      notifications = unrecognisedUserNotifications(unrecognisedUsers, settings.dryRun)
      // if in dry run, notifications list is empty
      notificationIds <- Attempt.traverse(notifications)(AnghammaradNotifications.send(_, anghammaradSnsArn, snsClient))
    } yield notificationIds
  }

  private def removeAccountPasswords(
      accountUnrecognisedUsers: AccountUnrecognisedUsers,
      iamClients: AwsClients[IamAsyncClient],
      dryRun: Boolean
  )(implicit ec: ExecutionContext): Attempt[List[Option[DeleteLoginProfileResponse]]] = {
    if (!dryRun) {
      Attempt.traverse(accountUnrecognisedUsers.unrecognisedUsers)(user =>
        IAMClient
          .deleteLoginProfile(accountUnrecognisedUsers.account, user.username, iamClients)
          .tap(emitRemovePasswordMetrics)
      )
    } else {
      logger.info(
        s"DRY RUN: Would delete passwords in account '${accountUnrecognisedUsers.account.name}' for IAM users: ${accountUnrecognisedUsers.unrecognisedUsers.map(_.username).mkString("'", "', '", "'")}."
      )
      Attempt.Right(Nil)
    }
  }

  private def emitRemovePasswordMetrics[T](result: Either[FailedAttempt, T]): Unit = {
    result.fold(
      { (failure: FailedAttempt) =>
        logger.error(s"failed to delete at least one password: ${failure.logMessage}")
        Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.failure, 1)
      },
      { (_: T) => Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.success, 1) }
    )
  }

  private[unrecognised] def disableAccountAccessKeys(
      accountUnrecognisedKeys: AccountUnrecognisedAccessKeys,
      iamClients: AwsClients[IamAsyncClient],
      dryRun: Boolean
  )(implicit ec: ExecutionContext): Attempt[List[UpdateAccessKeyResponse]] = {
    if (!dryRun) {
      val AccountUnrecognisedAccessKeys(account, accessKeys) = accountUnrecognisedKeys
      val activeAccessKeys = accessKeys.filter(_.status == CredentialActive)
      Attempt.traverse(activeAccessKeys)(key =>
        IAMClient.disableAccessKey(account, key.username, key.accessKeyId, iamClients).tap(emitDisabledAccessKeyMetrics)
      )
    } else {
      if (accountUnrecognisedKeys.vulnerableAccessKey.nonEmpty) {
        logger.info(
          s"DRY RUN: Would disable access keys in account '${accountUnrecognisedKeys.account.name}' for IAM users: ${accountUnrecognisedKeys.vulnerableAccessKey.map(_.username).mkString("'", "', '", "'")}."
        )
      }
      Attempt.Right(Nil)
    }
  }

  private def emitDisabledAccessKeyMetrics[T](result: Either[FailedAttempt, T]): Unit = {
    result.fold(
      { (failure: FailedAttempt) =>
        logger.error(s"Failed to disable unrecognised user access key: ${failure.logMessage}")
        Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.failure)
      },
      { (_: T) => Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.success) }
    )
  }

  private def logCredentialReportResults(
      credentialReports: Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]
  ): Unit = credentialReports.foreach {
    case (a, Left(e)) =>
      logger.error(s"Credentials report for account '${a.name}' failed to generate: ${e.logMessage}.")
    case (a, Right(r)) =>
      logger.info(s"Credentials report for account '${a.name}' generated at ${r.reportDate}.")
  }
}
