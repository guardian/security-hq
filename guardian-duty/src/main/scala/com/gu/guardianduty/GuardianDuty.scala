package com.gu.guardianduty

import com.gu.anghammarad.models.*
import io.circe.parser.*

import java.time.LocalDate
import scala.concurrent.Await
import scala.concurrent.duration.*

object GuardianDuty {

  /** Severity threshold — only notify for HIGH (7.0–8.9) and CRITICAL (9.0–10.0).
    */
  private val minimumSeverity: Double = 7.0

  /** Core processing function. Parses the raw EventBridge JSON, filters by severity, and sends a notification via the
    * provided notification service.
    *
    * @param rawJson
    *   the full EventBridge event JSON string
    * @param services
    *   application dependencies (logger, notifications, etc.)
    * @return
    *   Unit (side-effecting)
    */
  def processEvent(
      rawJson: String,
      services: Services,
      today: LocalDate
  ): Unit = {
    services.logger.log("Processing GuardDuty finding")
    val finding = decode[GuardDutyEventBridgeEvent](rawJson) match {
      case Left(err) =>
        services.logger.log(s"Failed to parse event JSON: ${err.getMessage}")
        throw err
      case Right(event) => event.detail
    }
    services.logger.log(
      s"Finding: severity=${finding.severity}, type=${finding.`type`}, title=${finding.title}"
    )

    if (finding.severity >= minimumSeverity) {
      val notification = findingAsNotification(finding, today)
      val notificationId = Await.result(
        services.notifications.notify(notification),
        30.seconds
      )
      services.logger.log(s"Notification sent: $notificationId")
    } else {
      services.logger.log(
        s"Finding ${finding.title} with severity ${finding.severity} below threshold $minimumSeverity, skipping notification."
      )
    }
  }

  private def calculateSeverityLabel(severity: Double): String = {
    if (severity >= 9.0) "CRITICAL"
    else if (severity >= 7.0) "HIGH"
    else if (severity >= 4.0) "MEDIUM"
    else "LOW"
  }

  private def findingAsNotification(
      finding: GuardDutyFinding,
      today: LocalDate
  ): Notification = {
    val severityLabel = calculateSeverityLabel(finding.severity)
    val rawSubject = s"[$severityLabel] GuardDuty: ${finding.`type`}"
    val subject =
      // SNS enforces a 100-character limit on the Subject field
      if (rawSubject.length > 100) rawSubject.take(97) + "..."
      else rawSubject
    val optionalTitle = {
      // for some finding types the title and description are identical
      if (finding.title != finding.description) s"${finding.title}\n\n"
      else ""
    }
    val message =
      s"""${optionalTitle}AWS account: `${finding.accountId}`
         |Region: `${finding.region}`
         |Severity: ${finding.severity} [$severityLabel]
         |
         |${finding.description}""".stripMargin

    Notification(
      subject = subject,
      message = message,
      actions = List(Action("View in GuardDuty console", finding.consoleUrl)),
      target = List(
        GithubTeamSlug("devx-security")
      ),
      channel = Preferred(HangoutsChat),
      sourceSystem = "guardian-duty",
      // Include date string in thread key to make updates on following days
      // appear in new threads, avoiding them remaining lost and unseen in old
      // messages
      threadKey = Some(s"${finding.id}-$today")
    )
  }
}
