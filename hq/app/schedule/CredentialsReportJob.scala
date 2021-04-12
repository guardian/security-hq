import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import logic.DateUtils
import model.{AccessKey, AwsAccount, CredentialReportDisplay, CronSchedule}
import play.api.Logging
import schedule.CredentialsNotifier.{createMessage, createNotification, send}
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class CredentialsReportJob(enabled: Boolean, cacheService: CacheService)(executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.onceADayAt1am
  val topicArn: String = ???

  def run(): Unit = {
    if (!enabled) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")

      val someValue: List[Either[FailedAttempt, Notification]] = getCredentialsReport().toList.map{ case (awsAccount, eFCreds) =>
        eFCreds.map{ credsReport =>
          val outdatedKeys = findOldAccessKeys(credsReport)
          val missingMfa = findMissingMfa(credsReport)
          val message = createMessage(outdatedKeys, missingMfa)
          createNotification(Account(awsAccount.id), message)
        }
      }
      someValue.foreach{ result: Either[FailedAttempt, Notification] =>
        result match {
          case Left(value) =>
            logger.info(s"check this issue out security devs! $value")
          case Right(email) =>
            send(email, topicArn, ???)(executionContext)
            logger.info(s"Completed scheduled job: $description")
        }
       }
    }
  }

  def getCredentialsReport(): Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = cacheService.getAllCredentials

  def findOldAccessKeys(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    //TODO write tests:Return true if any key is older than 90 days
    def hasOutdatedKey(keys: List[AccessKey]): Boolean = keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > 90L) //TODO fix Long issue
    val filteredMachines = credsReport.machineUsers.filter(user => hasOutdatedKey(List(user.key1, user.key2)))
    val filteredHumans = credsReport.humanUsers.filter(user => hasOutdatedKey(List(user.key1, user.key2)))
    credsReport.copy(machineUsers = filteredMachines, humanUsers = filteredHumans)
  }

  def findMissingMfa(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    credsReport.copy(humanUsers = credsReport.humanUsers.filterNot(_.hasMFA))
  }
}