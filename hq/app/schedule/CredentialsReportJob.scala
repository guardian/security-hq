import aws.AwsClients
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import logic.DateUtils
import model._
import play.api.Logging
import schedule.CredentialsMessages.createMessage
import schedule.CredentialsNotifier.{createNotification, send}
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class CredentialsReportJob(enabled: Boolean, cacheService: CacheService, snsClients: AwsClients[AmazonSNSAsync])(executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.onceADayAt1am
  //TODO retrieve from config
  val topicArn: String = ???

  def run(): Unit = {
    if (!enabled) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")

      //TODO: make into function and add tests
      val someValue: List[Either[FailedAttempt, Notification]] = getCredentialsReport().toList.map{ case (awsAccount, eFCreds) =>
        eFCreds.map{ credsReport =>
          val outdatedKeys = outdatedKeysInfo(findOldAccessKeys(credsReport))
          val missingMfa = missingMfaInfo(findMissingMfa(credsReport))
          val message = createMessage(outdatedKeys, missingMfa)
          createNotification(Account(awsAccount.id), message)
        }
      }
      for {
        snsClient <- snsClients
      } yield {
        someValue.foreach{ result: Either[FailedAttempt, Notification] =>
          result match {
            case Left(error) =>
              logger.info(s"failed to collect credentials report: $error")
            case Right(email) =>
              send(email, topicArn, snsClient.client)(executionContext)
              logger.info(s"Completed scheduled job: $description")
          }
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

  private def outdatedKeysInfo(outdatedKeys: CredentialReportDisplay): Seq[UserWithOutdatedKeys] = {
    outdatedKeys.humanUsers.map{ user =>
      UserWithOutdatedKeys(
        user.username,
        user.key1.lastRotated,
        user.key2.lastRotated,
        user.lastActivityDay
      )
    }
  }
  private def missingMfaInfo(missingMfa: CredentialReportDisplay): Seq[UserNoMfa] = {
    missingMfa.humanUsers.map{ user =>
      UserNoMfa(
        user.username,
        user.lastActivityDay
      )
    }
  }
}