package com.gu.hq.lambda.model

case class UserIdGroupPair(
  userId: String,
  groupId: String,
  groupName: Option[String],
  vpcId: Option[String],
  vpcPeeringConnectionId: Option[String],
  peeringStatus: Option[String]
)

case class IpPermission(
  ipProtocol: String,
  fromPort: Option[Int],
  toPort: Option[Int],
  userIdGroupPairs: List[UserIdGroupPair],
  ipRanges: List[String],
  prefixListIds: List[String]
)

case class SGConfiguration(
  ownerId: String,
  groupName: String,
  groupId: String,
  description: String,
  ipPermissions: List[IpPermission],
  ipPermissionsEgress: List[IpPermission],
  vpcId: String,
  tags: List[Tag]
)
