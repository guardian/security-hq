package model

import org.joda.time.DateTime

case class AwsAccount(
  id: String,
  name: String,
  roleArn: String
)

case class IAMCredential(
  user: String,
  arn: String,
  user_creation_time: String,
  password_enabled: String,
  password_last_used: String,
  password_last_changed: String,
  password_next_rotation: String,
  mfa_active: String,
  access_key_1_active: String,
  access_key_1_last_rotated: String,
  access_key_1_last_used_date: String,
  access_key_1_last_used_region: String,
  access_key_1_last_used_service: String,
  access_key_2_active: String,
  access_key_2_last_rotated: String,
  access_key_2_last_used_date: String,
  access_key_2_last_used_region: String,
  access_key_2_last_used_service: String,
  cert_1_active: String,
  cert_1_last_rotated: String,
  cert_2_active: String,
  cert_2_last_rotated: String
)

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
  isSuppressed: Boolean
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


sealed trait Stage

case object DEV extends Stage

case object PROD extends Stage
