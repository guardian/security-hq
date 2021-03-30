package model

import com.amazonaws.regions.Region
import com.google.cloud.securitycenter.v1.Finding
import com.google.protobuf.{Timestamp, Value}
import org.joda.time.DateTime

case class AwsAccount(
  id: String,
  name: String,
  roleArn: String
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

sealed trait ReportStatus
object Red extends ReportStatus
object Green extends ReportStatus
object Amber extends ReportStatus
object Blue extends ReportStatus

case class HumanUser(
  username: String,
  hasMFA : Boolean,
  key1: AccessKey,
  key2: AccessKey,
  reportStatus: ReportStatus,
  lastActivityDay : Option[Long],
  stack: Option[AwsStack]
)
case class MachineUser(
  username: String,
  key1: AccessKey,
  key2: AccessKey,
  reportStatus: ReportStatus,
  lastActivityDay: Option[Long],
  stack: Option[AwsStack]
)

case class SnykToken(value: String) extends AnyVal

case class SnykOrganisationName(value: String) extends AnyVal

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
  severity: String,
  eventTime: DateTime,
  explanation: Option[String],
  recommendation: Option[String]
)

//TODO delete after /gcp dev work complete
object GcpFinding {
  val longText: String = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
  val test: List[GcpFinding] = List(
    GcpFinding("TEST PROJECT A", "TEST", "High", DateTime.now, Some(longText), Some("TEST")),
    GcpFinding("TEST PROJECT A", "TEST", "Medium", DateTime.now, Some("TEST"), Some(longText)),
    GcpFinding("TEST PROJECT A", "TEST", "Low", DateTime.now, Some("TEST"), Some("TEST")),
    GcpFinding("TEST PROJECT A", "TEST", "Unknown", DateTime.now, Some("TEST"), Some("TEST")),
    GcpFinding("TEST PROJECT B", "TEST", "High", DateTime.now, Some("TEST"), Some("TEST")),
    GcpFinding("TEST PROJECT B", "TEST", "Medium", DateTime.now, Some("TEST"), Some("TEST")),
    GcpFinding("TEST PROJECT B", "TEST", "Low", DateTime.now, Some("TEST"), Some(longText)),
    GcpFinding("TEST PROJECT B", "TEST", "Unknown", DateTime.now, Some(longText), Some("TEST")),
  )
}

case class GcpSccConfig(orgId: String, sourceId: String)
