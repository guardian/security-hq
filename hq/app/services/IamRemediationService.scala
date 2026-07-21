package services

import aws.AwsClients
import aws.s3.S3.getS3Object
import com.gu.janus.JanusConfig
import config.Config
import config.Config.{getAnghammaradSNSTopicArn, getIamDynamoTableName, getIamUnrecognisedUserConfig}
import db.IamRemediationDb
import logic.IamUnrecognisedUsers.*
import logic.{IamOutdatedCredentials, IamUnrecognisedUsers}
import notifications.AnghammaradNotifications
import org.joda.time.{DateTime, DateTimeConstants}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment, Mode}
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsAsyncClient
import utils.Scheduler
import utils.attempt.Attempt

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

/** A collection of jobs for automatically fixing IAM problems in our AWS accounts.
  *
  * These jobs either directly intervene to fix security misconfigurations, or they do so only after sending warning
  * notifications to the account administrators.
  *
  * In cases where notifications are a requirement, DynamoDB is used to store a log of what notifications have been
  * sent.
  */
class IamRemediationService(
    cacheService: CacheService,
    snsClient: SnsAsyncClient,
    dynamo: IamRemediationDb,
    config: Configuration,
    iamClients: AwsClients[IamAsyncClient],
    lifecycle: ApplicationLifecycle,
    environment: Environment,
    securityS3Client: S3Client
)(implicit ec: ExecutionContext)
    extends Scheduler {

  /** Removes AWS access for colleagues that have departed.
    *
    * This feature is targeted at "recovery access", where teams keep one or two IAM users that can be used to gain
    * access to AWS when Janus is down. These recovery users have a password (and MFA) but do not have credentials. They
    * should also be tagged with the Google username of the individual so we can identify them.
    *
    * We then load data from the Guardian's Janus configuration and decide who is "recognised" by comparing this data
    * with google identity tags. If we find an IAM user tagged with an identity that is not in Janus, we can assume they
    * have left and disable the IAM user.
    */
  private def disableUnrecognisedUsers()(implicit ec: ExecutionContext): Attempt[Unit] = {
    val result = for {
      unrecognisedUsersConfig <- getIamUnrecognisedUserConfig(config)
      // fetch and parse our stored Janus config to use the canonical source of "recognised" usernames
      s3Object <- getS3Object(
        securityS3Client,
        unrecognisedUsersConfig.janusUserBucket,
        unrecognisedUsersConfig.janusDataFileKey
      )
      janusData = JanusConfig.load(makeFile(s3Object.mkString))
      janusUsernames = getJanusUsernames(janusData)
      // look up the credentials report from the cache service as our source of current IAM users
      accountCredsReports = IamUnrecognisedUsers.getCredsReportDisplayForAccount(cacheService.getAllCredentials)
      // determine the unrecognised users by comparing Janus usernames to the IAM users (and filter to allowed accounts)
      allowedAccountsUnrecognisedUsers = unrecognisedUsersForAllowedAccounts(
        accountCredsReports,
        janusUsernames,
        unrecognisedUsersConfig.allowedAccounts
      )
      // list the access keys associated to each user (this is required because the credentials report does not include access key ID)
      unrecognisedUserAccessKeys <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(
        listAccountAccessKeys(_, iamClients)
      )
      // disable each access key for unrecognised users
      _ <- Attempt.traverse(unrecognisedUserAccessKeys)(
        disableAccountAccessKeys(_, iamClients, unrecognisedUsersConfig.dryRun)
      )
      // remove passwords (i.e. login profiles) for each unrecognised user
      _ <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(
        removeAccountPasswords(_, iamClients, unrecognisedUsersConfig.dryRun)
      )
      // construct and send a notification for each unrecognised user
      notifications = unrecognisedUserNotifications(allowedAccountsUnrecognisedUsers, unrecognisedUsersConfig.dryRun)
      notificationIds <- Attempt.traverse(notifications)(
        AnghammaradNotifications.send(_, unrecognisedUsersConfig.anghammaradSnsTopicArn, snsClient)
      )
    } yield notificationIds
    result.tap {
      case Left(failedAttempt)    => logger.error(s"Failed to run unrecognised user job: ${failedAttempt.logMessage}")
      case Right(notificationIds) =>
        logger.info(s"Successfully ran unrecognised user job and sent ${notificationIds.length} notifications.")
    }.unit
  }

  if (environment.mode != Mode.Test) {
    // Schedule the observable on weekdays only as we may make changes in accounts that affect live systems
    // if warnings are not heeded. Initial delay of 10 minutes, so that the cache service has time to populate
    val iamRemediationServiceSubscription: () => Future[Unit] =
      scheduleAtFixedRate(
        initialDelay = 10.minutes,
        interval = 1.minute
      ) { () =>
        val now = DateTime.now()
        val isWeekday = now.getDayOfWeek != DateTimeConstants.SATURDAY && now.getDayOfWeek != DateTimeConstants.SUNDAY
        val isTimeToRun = (now.getHourOfDay == 9 && now.getMinuteOfHour == 0) ||
          (now.getHourOfDay == 14 && now.getMinuteOfHour == 0)

        if (isWeekday && isTimeToRun) {
          disableOutdatedCredentials()
          disableUnrecognisedUsers()
        }
      }

    lifecycle.addStopHook(iamRemediationServiceSubscription)
  }

  private def disableOutdatedCredentials(): Attempt[Unit] = for {
    notificationTopicArn <- getAnghammaradSNSTopicArn(config)
    tableName <- getIamDynamoTableName(config)
    serviceAccountIds <- Config.getAccountsForIamRemediationService(config)
    rawCredsReports = cacheService.getAllCredentials
    dryRun = Config.getOutdatedCredentialsDryRun(config)
    // this tells us which AWS accounts we are allowed to make changes to
    allowedAwsAccountIds <- Config.getAllowedAccountsForStage(config)
    result <- IamOutdatedCredentials(snsClient, iamClients, dynamo, dryRun).disableOutdatedCredentials(
      notificationTopicArn,
      tableName,
      serviceAccountIds,
      rawCredsReports,
      allowedAwsAccountIds
    )
  } yield result
}
