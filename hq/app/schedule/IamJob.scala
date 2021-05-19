package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import config.Config.getAnghammaradSNSTopicArn
import model._
import play.api.{Configuration, Logging}
import schedule.IamAudit.makeCredentialsNotification
import schedule.IamNotifier.send
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class IamJob(enabled: Boolean, cacheService: CacheService, snsClient: AmazonSNSAsync, config: Configuration)(executionContext: ExecutionContext) extends JobRunner with Logging {
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

    val credsReport: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = getCredsReport(cacheService)
    logger.info(s"successfully collected credentials report for $id. Report is not empty: ${credsReport.nonEmpty}.")
    makeCredentialsNotification(credsReport).foreach { notification =>
      send(notification, topicArn, snsClient)(executionContext)
    }
  }

  def getCredsReport(cacheService: CacheService): Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = cacheService.getAllCredentials
}
