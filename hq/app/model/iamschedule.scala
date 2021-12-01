package model

import com.amazonaws.regions.Regions
import com.gu.anghammarad.models.Target
import com.gu.anghammarad.models.{App, Notification, Stack, Target, Stage => AnghammaradStage}
import org.joda.time.DateTime
import play.api.libs.json.{JsString, Json, Writes}

case class CronSchedule(cron: String, description: String)

trait IAMAlert {
  def username: String
  def tags: List[Tag]
}
case class IAMAlertTargetGroup(
  targets: List[Target],
  users: Seq[VulnerableUser]
)

case class VulnerableUser(
  username: String,
  key1: AccessKey = AccessKey(NoKey, None),
  key2: AccessKey = AccessKey(NoKey, None),
  humanUser: Boolean,
  tags: List[Tag],
  disableDeadline: Option[DateTime] = None
) extends IAMAlert

object VulnerableUser {
  def fromIamUser(iamUser: IAMUser): VulnerableUser = {
    VulnerableUser(
      iamUser.username,
      iamUser.key1,
      iamUser.key2,
      iamUser.isHuman,
      iamUser.tags
    )
  }
}

case class VulnerableAccessKey(
  username: String,
  accessKeyWithId: AccessKeyWithId,
  humanUser: Boolean
)

sealed trait IamAuditNotificationType { def name: String }
object VulnerableCredential extends IamAuditNotificationType { val name = "vulnerableCredential" }
object UnrecognisedHumanUser extends IamAuditNotificationType { val name = "unrecognisedHumanUser" }

case class IamAuditAlert(`type`: IamAuditNotificationType, dateNotificationSent: DateTime, disableDeadline: DateTime)
object IamAuditAlert {
  implicit val jodaDateWrites: Writes[DateTime] = (d: DateTime) => JsString(d.toString())
  implicit val iamNotificationTypeWrites: Writes[IamAuditNotificationType] = (nt: IamAuditNotificationType) => JsString(nt.name)
  implicit val iamAuditAlertWrites = Json.writes[IamAuditAlert]
}
case class IamAuditUser(id: String, awsAccount: String, username: String, alerts: List[IamAuditAlert])
object IamAuditUser {
  implicit val iamAuditUserWrites = Json.writes[IamAuditUser]
}
case class IamNotification(warningN: Option[Notification], finalN: Option[Notification], alertedUsers: Seq[IamAuditUser])

case class UnrecognisedJobConfigProperties(
  allowedAccounts: List[String],
  janusDataFileKey: String,
  janusUserBucket: String,
  securityAccount: AwsAccount,
  anghammaradSnsTopicArn: String
)

