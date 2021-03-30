
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import logic.DateUtils
import model.{AccessKey, AwsAccount, CredentialReportDisplay, CronSchedule}
import play.api.Logging
import schedule.CredentialsNotifier.{createMessage, createNotification}
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.FailedAttempt
import com.gu.anghammarad.models.{AwsAccount => Account}

class CredentialsReportJob(enabled: Boolean, cacheService: CacheService) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.onceADayAt1am

  def run(): Unit = {
    if (!enabled) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
      for {
        (awsAccount, result) <- getCredentialsReport()
        credsReport <- result
        outdatedKeys = findOldAccessKeys(credsReport)
        missingMfa = findMissingMfa(credsReport)
        // could we create a function that has the same name, but takes diff args so that we can use this for diff use cases?
        message = createMessage(outdatedKeys, missingMfa)
        email = createNotification(Account(awsAccount.id), message)
        // create function that returns Either(failedAttempt, AWS was successful)
        // send email to anghammarad notfy method with aws account id
        sendEmailResponse <- send()
        // what does SNS respond with when we send it an email request? Is it an Either?
      } yield {
        logger.info(s"Completed scheduled job: $description")
        // TODO: could we log the failures and successes?
      }
    }
  }
  def getCredentialsReport(): Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = cacheService.getAllCredentials
  def findOldAccessKeys(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    def hasOutdatedKey(keys: List[AccessKey]): Boolean = {
      keys.exists{ key =>
        DateUtils.dayDiff(key.lastRotated).getOrElse(1) > 90
      }
    }
    val filteredMachines = credsReport.machineUsers.filter{ user =>
      hasOutdatedKey(List(user.key1, user.key2))
    }
    val filteredHumans = credsReport.humanUsers.filter{ user =>
      hasOutdatedKey(List(user.key1, user.key2))
    }
    credsReport.copy(machineUsers = filteredMachines, humanUsers = filteredHumans)
  }
  def findMissingMfa(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    credsReport.copy(humanUsers = credsReport.humanUsers.filterNot(_.hasMFA))
  }
}