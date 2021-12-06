package schedule.unrecognised

import aws.AwsClients
import aws.s3.S3.getS3Object
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{AwsAccount => TargetAccount}
import com.gu.janus.JanusConfig
import config.Config.getIamUnrecognisedUserConfig
import model.{AccessKeyEnabled, CronSchedule, VulnerableUser, AwsAccount => Account}
import play.api.{Configuration, Logging}
import schedule.IamMessages.FormerStaff.disabledUsersMessage
import schedule.IamMessages.disabledUsersSubject
import schedule.Notifier.{notification, send}
import schedule.unrecognised.IamUnrecognisedUsers.{getCredsReportDisplayForAccount, getJanusUsernames, makeFile, unrecognisedUsersForAllowedAccounts}
import schedule.vulnerable.IamDisableAccessKeys.disableAccessKeys
import schedule.vulnerable.IamRemovePassword.removePasswords
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class IamUnrecognisedUserJob(
  cacheService: CacheService,
  snsClient: AmazonSNSAsync,
  securityS3Client: AmazonS3,
  iamClients: AwsClients[AmazonIdentityManagementAsync],
  config: Configuration
)(implicit executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id: String = "unrecognised-iam-users"
  override val description: String = "Check for and remove unrecognised human IAM users"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay

  def run(testMode: Boolean): Unit = {
    if (testMode) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }

    val result: Attempt[List[Option[String]]] = for {
      config <- getIamUnrecognisedUserConfig(config)
      s3Object <- getS3Object(securityS3Client, config.janusUserBucket, config.janusDataFileKey)
      janusData = JanusConfig.load(makeFile(s3Object.mkString))
      janusUsernames = getJanusUsernames(janusData)
      accountCredsReports = getCredsReportDisplayForAccount(cacheService.getAllCredentials)
      allowedAccountsUnrecognisedUsers = unrecognisedUsersForAllowedAccounts(accountCredsReports, janusUsernames, config.allowedAccounts)
      _ <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(disableUser)
      notificationIds <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(sendNotification(_, testMode, config.anghammaradSnsTopicArn))
    } yield notificationIds
    result.fold(
      { failure =>
        logger.error(s"Failed to run unrecognised user job: ${failure.logMessage}")
      },
      { notificationIds =>
        logger.info(s"Successfully ran unrecognised user job and sent ${notificationIds.flatten.length} notifications.")
      }
    )
  }

  private def disableUser(accountCrd: (Account, List[VulnerableUser])): Attempt[List[String]] = {
    val (account, users) = accountCrd
    for {
      disableKeyResult <- disableAccessKeys(account, users, iamClients)
      removePasswordResults <- Attempt.traverse(users)(removePasswords(account, _, iamClients))
    } yield {
      disableKeyResult.map(_.getSdkResponseMetadata.getRequestId) ++ removePasswordResults.collect {
          case Some(result) => result.getSdkResponseMetadata.getRequestId
        }
    }
  }

  private def sendNotification(accountCrd: (Account, Seq[VulnerableUser]), testMode: Boolean, topicArn: String): Attempt[Option[String]] = {
    val (account, users) = accountCrd
    val usersWithAtLeastOneEnabledKeyOrHuman = users.filter( user =>
      user.key1.keyStatus == AccessKeyEnabled ||
        user.key2.keyStatus == AccessKeyEnabled ||
        user.humanUser
    )
      if (usersWithAtLeastOneEnabledKeyOrHuman.isEmpty) {
      Attempt.Right(None)
    } else {
      val message = notification(
        disabledUsersSubject(account),
        disabledUsersMessage(usersWithAtLeastOneEnabledKeyOrHuman),
        List(TargetAccount(account.accountNumber))
      )
      send(message, topicArn, snsClient, testMode).map(Some(_))
    }
  }
}
