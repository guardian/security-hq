package model

import com.amazonaws.regions.Region
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata
import com.google.cloud.securitycenter.v1.Finding.Severity
import com.gu.anghammarad.models.{App, Notification, Stack, Target, Stage => AnghammaradStage}
import org.joda.time.DateTime
import play.api.libs.json.{JsString, Json, Writes}


case class AwsAccount(
  id: String,
  name: String,
  roleArn: String,
  accountNumber: String
)

case class AwsStack(
  id: String,
  name: String,
  region: String
)

case class StackResource(
  stackId: String,
  stackName: String,
  physicalResourceId: String,
  logicalResourceId: String,
  resourceStatus: String,
  resourceType: String
)

case class IAMCredentialsReport(
  generatedAt: DateTime,
  entries: List[IAMCredential]
)

case class IAMCredential(
  user: String,
  arn: String,
  creationTime: DateTime,
  stack: Option[AwsStack],
  passwordEnabled: Option[Boolean],
  passwordLastUsed: Option[DateTime],
  passwordLastChanged: Option[DateTime],
  passwordNextRotation: Option[DateTime],
  mfaActive: Boolean,
  accessKey1Active: Boolean,
  accessKey1LastRotated: Option[DateTime],
  accessKey1LastUsedDate: Option[DateTime],
  accessKey1LastUsedRegion: Option[Region],
  accessKey1LastUsedService: Option[String],
  accessKey2Active: Boolean,
  accessKey2LastRotated: Option[DateTime],
  accessKey2LastUsedDate: Option[DateTime],
  accessKey2LastUsedRegion: Option[Region],
  accessKey2LastUsedService: Option[String],
  cert1Active: Boolean,
  cert1LastRotated: Option[DateTime],
  cert2Active: Boolean,
  cert2LastRotated: Option[DateTime],
  tags: List[Tag] = List()
                        ) {
  val rootUser = IAMCredential.isRootUser(user)
}

object IAMCredential {
  def isRootUser(user: String): Boolean = user == "<root_account>"
}

case class TrustedAdvisorCheck(
  id: String,
  name: String,
  description: String,
  category: String
)

case class TrustedAdvisorDetailsResult[A <: TrustedAdvisorCheckDetails](
  checkId: String,
  status: String,
  timestamp: DateTime,
  flaggedResources: List[A],
  resourcesIgnored: Long,
  resourcesFlagged: Long,
  resourcesSuppressed: Long
)

sealed trait TrustedAdvisorCheckDetails
case class SGOpenPortsDetail(
  status: String,
  region: String,
  name: String,
  id: String,
  vpcId: String,
  protocol: String,
  port: String,
  alertLevel: String,
  isSuppressed: Boolean,
  vpcName: Option[String] = None,
  stackId : Option[String] = None,
  stackName : Option[String] = None
) extends TrustedAdvisorCheckDetails
case class RDSSGsDetail(
  region: String,
  rdsSgId: String,
  ec2SGId: String,
  alertLevel: String,
  isSuppressed: Boolean
) extends TrustedAdvisorCheckDetails
case class ExposedIAMKeyDetail(
  keyId: String,
  username: String,
  fraudType: String,
  caseId: String,
  updated: String,
  location: String,
  deadline: String,
  usage: String
) extends TrustedAdvisorCheckDetails
case class BucketDetail(
  region: String,
  bucketName: String,
  status: String,
  aclAllowsRead: Boolean,
  aclAllowsWrite: Boolean,
  policyAllowsAccess: Boolean,
  isSuppressed: Boolean,
  reportStatus: Option[ReportStatus] = None,
  isEncrypted: Boolean = false
) extends TrustedAdvisorCheckDetails

sealed trait SGInUse
case class Ec2Instance(instanceId: String) extends SGInUse
case class ELB(description: String) extends SGInUse
case class EfsVolume(description: String) extends SGInUse
case class UnknownUsage(
  description: String,
  networkInterfaceId: String
) extends SGInUse

sealed trait Stage
case object DEV extends Stage
case object PROD extends Stage

case class CredentialReportDisplay(
  reportDate: DateTime,
  machineUsers: Seq[MachineUser] = Seq.empty,
  humanUsers: Seq[HumanUser] = Seq.empty
)

sealed trait KeyStatus
object AccessKeyEnabled extends KeyStatus
object AccessKeyDisabled extends KeyStatus
object NoKey extends KeyStatus

case class AccessKey(
  keyStatus: KeyStatus,
  lastRotated: Option[DateTime]
)

case class AccessKeyWithId(
  accessKey: AccessKey,
  id: String
)

object AccessKeyWithId {
  def fromAwsAccessKeyMetadata(awsAccessKeyMetadata: AccessKeyMetadata): AccessKeyWithId =
    AccessKeyWithId(
      AccessKey(
        keyStatusFromString(awsAccessKeyMetadata.getStatus),
        Option(new DateTime(awsAccessKeyMetadata.getCreateDate))
      ),
      awsAccessKeyMetadata.getAccessKeyId
    )

  private def keyStatusFromString(awsKeyStatus: String): KeyStatus =  awsKeyStatus match {
    case "Active" => AccessKeyEnabled
    case "Inactive" => AccessKeyDisabled
    case _ => NoKey
  }
}

sealed trait ReportStatus {
  def reasons(): Seq[ReportStatusReason] = Seq.empty
}
case class Red(override val reasons: Seq[ReportStatusReason] = Seq.empty) extends ReportStatus
case class Amber(override val reasons: Seq[ReportStatusReason] = Seq.empty) extends ReportStatus
case object Green extends ReportStatus
case object Blue extends ReportStatus


sealed trait ReportStatusReason
object MissingMfa extends ReportStatusReason
object OutdatedKey extends ReportStatusReason
object ActiveAccessKey extends ReportStatusReason
object MissingUsernameTag extends ReportStatusReason

case class Tag(key: String, value: String)
object Tag {

  val EMPTY_SSAID = "no-ssa-tags"

  def findAnghammaradTarget(key: String, toTarget: String => Target, tags: List[Tag]): Option[Target] = {
    val value = tags.find(_.key.toLowerCase() == key.toLowerCase()).map(_.value)
    value.map(toTarget)
  }

  def tagsToAnghammaradTargets(tags: List[Tag]): List[Target] = {
    List (
      findAnghammaradTarget("stack", Stack, tags),
      findAnghammaradTarget("stage", AnghammaradStage, tags),
      findAnghammaradTarget("app", App, tags),
    ).flatten
  }

  def tagsToSSAID(tags: List[Tag]): String = {
    val ssaTags = tags.filter(t => List("stack", "stage", "app").contains(t.key.toLowerCase))
    if (ssaTags.nonEmpty) {
      ssaTags.sortBy(_.key).map(_.value).mkString("-")
    } else {
      EMPTY_SSAID
    }
  }
}


sealed trait IAMUser {
  def username: String
  def key1: AccessKey
  def key2: AccessKey
  def reportStatus: ReportStatus
  def lastActivityDay: Option[Long]
  def stack: Option[AwsStack]
  def tags: List[Tag]
  def isHuman: Boolean
}

case class HumanUser(
  username: String,
  hasMFA: Boolean,
  key1: AccessKey,
  key2: AccessKey,
  reportStatus: ReportStatus,
  lastActivityDay: Option[Long],
  stack: Option[AwsStack],
  tags: List[Tag]
) extends IAMUser {
  val isHuman = true
}

case class MachineUser(
  username: String,
  key1: AccessKey,
  key2: AccessKey,
  reportStatus: ReportStatus,
  lastActivityDay: Option[Long],
  stack: Option[AwsStack],
  tags: List[Tag]
) extends IAMUser {
  val isHuman = false
}

case class SnykConfig(snykToken: SnykToken, snykGroupId: SnykGroupId)

case class SnykToken(value: String) extends AnyVal

case class SnykGroupId(value: String) extends AnyVal

case class SnykGroup(name: String, id: String)

case class SnykOrganisation(name: String, id: String, groupOpt: Option[SnykGroup])

case class SnykProject(name: String, id: String, organisation: Option[SnykOrganisation])

case class SnykIssue(title: String, id: String, severity: String)

case class SnykProjectIssues(project: Option[SnykProject], ok: Boolean, vulnerabilities: Set[SnykIssue])  {
  def high: Int = vulnerabilities.count(s => s.severity.equalsIgnoreCase("high"))
  def medium: Int = vulnerabilities.count(s => s.severity.equalsIgnoreCase("medium"))
  def low: Int = vulnerabilities.count(s => s.severity.equalsIgnoreCase("low"))
}

case class SnykError(error: String)

case class Documentation(title: String, description: String, icon: String, slug: String)

case class GcpReport(reportDate: DateTime, finding: Map[String, Seq[GcpFinding]] = Map.empty)

case class GcpFinding(
  project: String,
  category: String,
  severity: Severity,
  eventTime: DateTime,
  explanation: Option[String],
  recommendation: Option[String]
)

case class GcpSccConfig(orgId: String, sourceId: String)

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
object IamAuditNotificationType {
  def fromName(name: String): IamAuditNotificationType =
    Seq(VulnerableCredential, UnrecognisedHumanUser)
      .find(name == _.name)
      .getOrElse(VulnerableCredential)
}

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
