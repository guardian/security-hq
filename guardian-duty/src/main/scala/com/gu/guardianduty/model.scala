package com.gu.guardianduty

import io.circe.*

/** EventBridge envelope for a GuardDuty finding event. */
case class GuardDutyEventBridgeEvent(
    version: String,
    id: String,
    `detail-type`: String,
    source: String,
    account: String,
    time: String,
    region: String,
    detail: GuardDutyFinding
) derives Decoder

/** Subset of the GuardDuty finding detail.
  *
  * See https://docs.aws.amazon.com/guardduty/latest/ug/guardduty_findings-summary.html
  */
case class GuardDutyFinding(
    schemaVersion: Option[String],
    id: String,
    accountId: String,
    region: String,
    severity: Double,
    `type`: String,
    title: String,
    description: String,
    resource: Option[Json] // keep as raw JSON for now
) derives Decoder {
  val consoleUrl =
    s"https://$region.console.aws.amazon.com/guardduty/home?region=$region#/findings?fId=$id"
}
