package schedule.unrecognised

import aws.AwsClients
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{AwsAccount => TargetAccount}
import com.gu.janus.model.{ACL, AwsAccount, JanusData, SupportACL}
import config.Config.getAnghammaradSNSTopicArn
import model.{CronSchedule, VulnerableUser}
import org.joda.time.Seconds
import play.api.{Configuration, Logging}
import schedule.IamMessages.FormerStaff.disabledUsersMessage
import schedule.IamMessages.disabledUsersSubject
import schedule.Notifier.{notification, send}
import schedule.unrecognised.IamUnrecognisedUsers.{filterUnrecognisedIamUsers, getJanusUsernames}
import schedule.vulnerable.IamDisableAccessKeys.disableAccessKeys
import schedule.vulnerable.IamRemovePassword.removePasswords
import schedule.{CronSchedules, JobRunner}
import services.CacheService

import scala.concurrent.ExecutionContext

class IamUnrecognisedUserJob(cacheService: CacheService, snsClient: AmazonSNSAsync, iamClients: AwsClients[AmazonIdentityManagementAsync], config: Configuration)(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id: String = "unrecognised-iam-users"
  override val description: String = "Check for and remove unrecognised human IAM users"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay
  private val topicArn: Option[String] = getAnghammaradSNSTopicArn(config)

  def run(testMode: Boolean): Unit = {
    if (testMode) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }

    val dummyJanusData = JanusData(
      Set(AwsAccount("Deploy Tools", "deployTools")),
      ACL(Map("firstName.secondName" -> Set.empty)),
      ACL(Map.empty),
      SupportACL(Map.empty, Set.empty, Seconds.ZERO),
      None
    )

    val janusUsers: Seq[String] = getJanusUsernames(dummyJanusData)
    val credsReport = cacheService.getAllCredentials

    credsReport.map { case (account, eitherCredsReportOrFailure) =>
      eitherCredsReportOrFailure.map { credsReport =>
        val humanUsers = credsReport.humanUsers
        val unrecognisedIamUsers: Seq[VulnerableUser] = filterUnrecognisedIamUsers(humanUsers, "name", janusUsers)

        //TODO these should return a value that we can inspect for success/error handling
        disableAccessKeys(account, unrecognisedIamUsers, iamClients)
        removePasswords(account, unrecognisedIamUsers, iamClients)

        if (unrecognisedIamUsers.nonEmpty) {
          val message = notification(
            disabledUsersSubject(account),
            disabledUsersMessage(unrecognisedIamUsers),
            List(TargetAccount(account.accountNumber))
          )
          send(
            message,
            topicArn,
            snsClient,
            testMode
          )
        }
      }
    }
  }
}
