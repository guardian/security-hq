package schedule.unrecognised

import model.CronSchedule
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
}
