package schedule

import aws.AwsClients
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.Notification
import config.Config.getAnghammaradSNSTopicArn
import model._
import play.api.{Configuration, Logging}
import schedule.IamFlaggedUsers.getFlaggedCredentialsReports
import schedule.IamNotifications.makeNotification
import schedule.IamNotifier.send
import schedule.IamUsersToDisable.usersToDisable
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class IamJob(enabled: Boolean, cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: Dynamo,config: Configuration, iamClients: AwsClients[AmazonIdentityManagementAsync])(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay
  val topicArn: Option[String] = getAnghammaradSNSTopicArn(config)


  def run(testMode: Boolean): Unit = {
    if (!enabled) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }

    def getCredsReport(cacheService: CacheService): Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = cacheService.getAllCredentials

    val credsReport: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = getCredsReport(cacheService)
    logger.info(s"successfully collected credentials report for $id. Report is empty: ${credsReport.isEmpty}.")
    val flaggedCredentials = getFlaggedCredentialsReports(credsReport, dynamo)

    def sendNotificationAndRecord(notification: Notification, users: Seq[IamAuditUser]): Unit = {
      for {
        _ <- send(notification, topicArn, snsClient, testMode)
        _ = users.map(dynamo.putAlert)
      } yield ()
    }

    // send warning and final notifications
    makeNotification(flaggedCredentials).foreach { notification =>
      notification.warningN.foreach(sendNotificationAndRecord(_, notification.alertedUsers))
      notification.finalN.foreach(sendNotificationAndRecord(_, notification.alertedUsers))
    }

    // disable user if still vulnerable after notifications have been sent and send a final notification stating this
    usersToDisable(flaggedCredentials, dynamo).foreach { case (account, users) =>
      IamRemovePassword.removePasswords(account, users, iamClients)
      IamDisableAccessKeys.disableAccessKey(account, users, iamClients)
      val alertAwsAccountThatUserDisabled: Notification = ???
      send(alertAwsAccountThatUserDisabled, topicArn, snsClient, testMode)
    }
  }
}
