package model

import com.amazonaws.regions.Regions
import com.gu.anghammarad.models.Target
import com.gu.anghammarad.models.{App, Notification, Stack, Target, Stage => AnghammaradStage}
import org.joda.time.DateTime
import play.api.libs.json.{JsString, Json, Writes}

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

case class UnrecognisedJobConfigProperties(
  allowedAccounts: List[String],
  janusDataFileKey: String,
  janusUserBucket: String,
  securityAccount: AwsAccount,
  anghammaradSnsTopicArn: String
)

