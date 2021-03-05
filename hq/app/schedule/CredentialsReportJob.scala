
import model.CronSchedule
import play.api.{Logger, Logging}
import schedule.{CronSchedules, JobRunner}

class CredentialsReportJob(enabled: Boolean) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"

  override val cronSchedule: CronSchedule = CronSchedules.onceADayAt1am

  def run(): Unit = {
    if (!enabled) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")

      // retrieve/generate creds report
         // get from cacheservice : CredentialReportDisplay (for comp - for each account in our Map)
         // We can use case classes: HumanUser / MachineUser and their AccessKey
      // 1) get old perm creds (1 or more access keys)
      // 2) find accounts where IAM users have password enabled, but not MFA
      // create an email to the aws accounts with either of these 2 cases
      // send an email

      logger.info(s"Completed scheduled job: $description")
    }
  }
}