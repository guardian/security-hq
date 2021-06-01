package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import config.Config.getAnghammaradSNSTopicArn
import model._
import play.api.{Configuration, Logging}
import schedule.IamAudit.{getFlaggedCredentialsReports, makeIamNotification}
import schedule.IamNotifier.send
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class IamJob(enabled: Boolean, cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: Dynamo,config: Configuration)(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.firstMondayOfEveryMonth
  val topicArn: Option[String] = getAnghammaradSNSTopicArn(config)

  def run(): Unit = {
    if (!enabled) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }

    def getCredsReport(cacheService: CacheService): Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = cacheService.getAllCredentials
    val credsReport: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = getCredsReport(cacheService)
    logger.info(s"successfully collected credentials report for $id. Report is empty: ${credsReport.isEmpty}.")

    makeIamNotification(getFlaggedCredentialsReports(credsReport)).foreach { notification: IamNotification =>
      for {
        _ <- send(notification, topicArn, snsClient)
        _ = dynamo.putAlert(notification.iamUser)
      } yield ()
    }
  }
}
