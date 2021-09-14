package schedule.unrecognised

import com.gu.janus.model.{ACL, AwsAccount, JanusData, SupportACL}
import model.CronSchedule
import org.joda.time.Seconds
import play.api.Logging
import schedule.{CronSchedules, JobRunner}

class IamUnrecognisedUserJob() extends JobRunner with Logging {
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

  val dummyJanusData = JanusData(
    Set(AwsAccount("Deploy Tools", "deployTools")),
    ACL(Map("firstName.secondName" -> Set.empty)),
    ACL(Map.empty),
    SupportACL(Map.empty, Set.empty, Seconds.ZERO),
    None
  )

  //TODO
  // grab all IAM users
  //compare tags on IAM users to the list
}
