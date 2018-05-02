package com.gu.hq.lambda.model

import org.joda.time.DateTime
import play.api.libs.json.JsValue


case class InvokingEvent(
  configurationItem: Option[ConfigurationItem],
  configurationItemDiff: Option[ConfigurationItemDiff],
  notificationCreationTime: DateTime,
  messageType: String,
  recordVersion: String
)

case class ConfigurationItemDiff(
  changeType: String,
  changedProperties: Map[String, JsValue]
)

case class ConfigurationItem(
  version: Option[String],
  accountId: Option[String],
  configurationItemCaptureTime: Option[DateTime],
  configurationItemStatus: Option[String],
  configurationStateId: Option[Long],
  configurationItemMD5Hash: Option[String],
  ARN: Option[String],
  resourceType: Option[String],
  resourceId: Option[String],
  resourceName: Option[String],
  awsRegion: Option[String],
  availabilityZone: Option[String],
  resourceCreationTime: Option[DateTime],
  tags: Map[String, String],
  relatedEvents: List[String],
  relationships: List[Relationship],
  configuration: JsValue,
  supplementaryConfiguration: Option[Map[String, String]]
)

case class Relationship(
  resourceType: String,
  resourceId: String,
  resourceName: Option[String],
  name: String
)