package schedule.unrecognised

import com.gu.janus.model.{ACL, AwsAccount, JanusData, SupportACL}
import model.{CredentialReportDisplay, CronSchedule, HumanUser, AwsAccount => Account}
import org.joda.time.Seconds
import play.api.Logging
import schedule.unrecognised.IamUnrecognisedUsers.{filterUnrecognisedIamUsers, getHumanUsers, getJanusUsernames}
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.FailedAttempt

class IamUnrecognisedUserJob(cacheService: CacheService) extends JobRunner with Logging {
  override val id: String = "unrecognised-iam-users"
  override val description: String = "Check for and remove unrecognised human IAM users"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay

  def run(testMode: Boolean): Unit = {
    if (testMode) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }
  }

  val dummyJanusData: JanusData = ???
  val janusUsers: List[String] = getJanusUsernames(dummyJanusData)
  val credsReport = cacheService.getAllCredentials
  val iamHumanUsers: List[HumanUser] = getHumanUsers(credsReport)
  val unrecognisedIamUsers: List[HumanUser] = filterUnrecognisedIamUsers(iamHumanUsers, "name", janusUsers)

  //TODO disable access key and remove password
  //TODO send notification that this has been done
}
