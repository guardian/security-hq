package com.gu.shq.outdatedcredentials

import com.gu.shq.model.DEV
import com.gu.shq.utils.attempt.Attempt

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

@main def runOutdatedCredentials(): Unit = {
  given ExecutionContext = ExecutionContext.global

  val settings = Settings(
    stack = "testStack",
    stage = DEV,
    dryRun = true,
    configBucket = "security-dist",
    configKey = "security/DEV/security-hq/security-hq.conf"
  )

  val result: Attempt[Unit] = IamOutdatedCredentials.disableOutdatedCredentials(settings)

  Await.result(result.underlying, 10.minutes) match {
    case Right(_) =>
      println("Completed successfully")

    case Left(failedAttempt) =>
      Console.err.println(
        s"IamOutdatedCredentials Main execution failed: ${failedAttempt.logMessage}"
      )
      failedAttempt.firstException.foreach(Console.err.println)
      sys.exit(1)
  }
}
