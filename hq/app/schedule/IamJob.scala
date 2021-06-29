package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.Notification
import config.Config.getAnghammaradSNSTopicArn
import model._
import play.api.{Configuration, Logging}
import schedule.IamAudit.{getFlaggedCredentialsReports, makeNotification}
import schedule.IamNotifier.send
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class IamJob(enabled: Boolean, cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: Dynamo,config: Configuration)(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.firstMondayOfEveryMonth
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

    def sendNotificationAndRecord(notification: Notification, users: Seq[IamAuditUser]): Unit = {
      for {
        _ <- send(notification, topicArn, snsClient, testMode)
        _ = users.map(dynamo.putAlert)
      } yield ()
    }

    makeNotification(getFlaggedCredentialsReports(credsReport, dynamo)).foreach { notification =>
      notification.warningN.foreach(sendNotificationAndRecord(_, notification.alertedUsers))
      notification.finalN.foreach(sendNotificationAndRecord(_, notification.alertedUsers))
    }
  }
}
