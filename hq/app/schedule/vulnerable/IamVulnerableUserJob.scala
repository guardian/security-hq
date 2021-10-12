package schedule.vulnerable

import aws.AwsClients
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import config.Config.getAnghammaradSNSTopicArn
import model._
import play.api.{Configuration, Logging}
import schedule.IamMessages.VulnerableCredentials.disabledUsersMessage
import schedule.IamMessages.disabledUsersSubject
import schedule.IamNotifications.makeNotifications
import schedule.IamUsersToDisable.usersToDisable
import schedule.Notifier.{notification, send}
import schedule.vulnerable.IamDeadline.getVulnerableUsersToAlert
import schedule.vulnerable.IamDisableAccessKeys.disableAccessKeys
import schedule.vulnerable.IamRemovePassword.removePasswords
import schedule.{CronSchedules, DynamoAlertService, JobRunner}
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class IamVulnerableUserJob(cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: DynamoAlertService, config: Configuration, iamClients: AwsClients[AmazonIdentityManagementAsync])(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "vulnerable-iam-users"
  override val description = "Automated notifications and disablement of vulnerable permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay
  val topicArn: Option[String] = getAnghammaradSNSTopicArn(config)


  def run(testMode: Boolean): Unit = {
    if (testMode) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }

    val credsReport: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = cacheService.getAllCredentials
    logger.info(s"successfully collected credentials report for $id. Report is empty: ${credsReport.isEmpty}.")
    val flaggedCredentials = IamFlaggedUsers.getVulnerableUsers(credsReport)

    val usersToAlert = getVulnerableUsersToAlert(flaggedCredentials, dynamo)

    def sendNotificationAndRecord(notification: Notification, users: Seq[IamAuditUser]): Unit = {
      for {
        _ <- send(notification, topicArn, snsClient, testMode)
        _ = users.map(dynamo.putAlert)
      } yield ()
    }

    // send warning and final notifications
    makeNotifications(usersToAlert).foreach { notification =>
      notification.warningN.foreach(sendNotificationAndRecord(_, notification.alertedUsers))
      notification.finalN.foreach(sendNotificationAndRecord(_, notification.alertedUsers))
    }

    // disable user if still vulnerable after notifications have been sent and send a final notification stating this
    usersToDisable(flaggedCredentials, dynamo).foreach { case (account, users) =>
      removePasswords(account, users, iamClients)
      disableAccessKeys(account, users, iamClients)

      if (users.nonEmpty) {
        logger.info(s"attempting to notify ${account.name} that the following users have been disabled: ${users.map(_.username)}")
        send(
          notification(disabledUsersSubject(account), disabledUsersMessage(users), List(Account(account.accountNumber))),
          topicArn,
          snsClient,
          testMode
        )
      }
    }
  }
}
