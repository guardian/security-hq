package com.gu.guardianduty

import java.time.LocalDate
import scala.io.Source

/** CLI entry point for local testing. Reads event JSON from stdin or a file argument.
  *
  * Example:
  *
  * $ sbt "run src/test/resources/test-events/sample-guardduty-event.json"
  */
@main def run(args: String*): Unit = {
  val rawJson = args.headOption match {
    case Some(filePath) =>
      val src = Source.fromFile(filePath)
      try src.mkString
      finally src.close()
    case None =>
      val src = Source.stdin
      try src.mkString
      finally src.close()
  }

  val services = Services(
    notifications = ConsoleNotifications(),
    logger = ConsoleLogger()
  )

  val today = LocalDate.now()

  GuardianDuty.processEvent(rawJson, services, today)
}
