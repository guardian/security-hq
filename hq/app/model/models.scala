package model

import com.amazonaws.regions.Region
import org.joda.time.DateTime

case class AwsAccount(
  id: String,
  name: String,
  roleArn: String
)

case class IAMCredentialsReport(
  generatedAt: DateTime,
  entries: List[IAMCredential]
)

case class IAMCredential(
  user: String,
  arn: String,
  creationTime: DateTime,
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
  cert2LastRotated: Option[DateTime]
                        ) {
  val rootUser = user == "<root_account>"
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
  vpcName: Option[String] = None
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

sealed trait SGInUse
case class Ec2Instance(instanceId: String) extends SGInUse
case class ELB(description: String) extends SGInUse
case class UnknownUsage(
  description: String,
  networkInterfaceId: String
) extends SGInUse

sealed trait Stage
case object DEV extends Stage
case object PROD extends Stage

case class CredentialReportDisplay(
  reportDate : DateTime,
  machineUsers: Seq[MachineUser] = Seq.empty,
  humanUsers: Seq[HumanUser] = Seq.empty
)

sealed trait KeyStatus
object AccessKeyEnabled extends KeyStatus
object AccessKeyDisabled extends KeyStatus
object NoKey extends KeyStatus


sealed trait ReportStatus
object Red extends ReportStatus
object Green extends ReportStatus
object Amber extends ReportStatus

case class HumanUser(
  username: String,
  hasMFA : Boolean,
  key1Status: KeyStatus,
  key2Status: KeyStatus,
  reportStatus: ReportStatus,
  lastActivityDay : Option[Long]
)
case class MachineUser  (
  username: String,
  key1Status: KeyStatus,
  key2Status: KeyStatus,
  reportStatus: ReportStatus,
  lastActivityDay : Option[Long]
)

class Token(val value: String) extends AnyVal

class Organisation(val value: String) extends AnyVal

case class SnykOrg(name: String, id: String)

case class SnykProject(name: String, id: String)

case class SnykIssue(title: String, id: String, severity: String)

case class SnykProjectIssues(name: String, id: String, ok: Boolean, vulnerabilities: List[SnykIssue])  {
  def withName(name: String) = new SnykProjectIssues(name, this.id, this.ok, this.vulnerabilities)
  def withId(id: String) = new SnykProjectIssues(this.name, id, this.ok, this.vulnerabilities)
  def high = vulnerabilities.filter(s => s.severity.equalsIgnoreCase("high")).length
  def medium = vulnerabilities.filter(s => s.severity.equalsIgnoreCase("medium")).length
  def low = vulnerabilities.filter(s => s.severity.equalsIgnoreCase("low")).length
}

case class SnykError(error: String)
