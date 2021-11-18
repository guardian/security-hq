package schedule.vulnerable

import aws.AwsClients
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import config.Config.getAnghammaradSNSTopicArn
import model._
import play.api.{Configuration, Logging}
import schedule.IamMessages.VulnerableCredentials.disabledUsersMessage
import schedule.IamMessages.disabledUsersSubject
import schedule.IamNotifications.makeNotifications
import schedule.IamUsersToDisable.usersToDisable
import schedule.Notifier.{notification, send}
import schedule.vulnerable.IamDeadline.getVulnerableUsersToAlert
import schedule.vulnerable.IamDisableAccessKeys.disableAccessKeys
import schedule.vulnerable.IamRemovePassword.removePasswords
import schedule.{CronSchedules, DynamoAlertService, JobRunner}
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class IamVulnerableUserJob(cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: DynamoAlertService, config: Configuration, iamClients: AwsClients[AmazonIdentityManagementAsync])(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "vulnerable-iam-users"
  override val description = "Automated notifications and disablement of vulnerable permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay

  def run(testMode: Boolean): Unit = {
    if (testMode) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }
  }
}
