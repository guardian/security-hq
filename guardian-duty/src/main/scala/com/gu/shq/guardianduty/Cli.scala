package com.gu.shq.guardianduty

import java.time.LocalDate
import scala.io.Source

/** CLI entry point for local testing. Reads event JSON from stdin or a file argument.
  *
  * Example:
  *
  * $ sbt "guardianDuty/run guardian-duty/src/test/resources/test-events/sample-guardduty-event.json"
  */
@main def runGuardianDuty(args: String*): Unit = {
  // assume working directory is repo root!
  val src = args.headOption match {
    case Some("-")      => Source.stdin
    case Some(filePath) => Source.fromFile(filePath)
    case None           => Source.fromFile("guardian-duty/src/test/resources/test-events/sample-guardduty-event.json")
  }
  val rawJson = {
    try src.mkString
    finally src.close()
  }

  val services = Services(
    notifications = ConsoleNotifications(),
    logger = ConsoleLogger()
  )

  val today = LocalDate.now()

  GuardianDuty.processEvent(rawJson, services, today, runbookUrl = "[runbook url not available when running locally]")
}
