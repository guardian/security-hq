package com.gu.shq.unrecognisedusers

import software.amazon.awssdk.regions.Region

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/** Local entrypoint for running the unrecognised-users job outside lambda.
  *
  * Configuration is hardcoded to appropriate values for running locally; edit them if needed for your specific use
  * case.
  */
@main def runUnrecognisedUsers(): Unit = {
  given ExecutionContext = ExecutionContext.global

  val settings = Settings(
    dryRun = true,
    configBucket = "security-dist",
    configKey = "security/DEV/security-hq/security-hq.conf",
    janusBucket = "gu-security-hq-audit",
    janusKey = "security/DEV/janus-data-export/janusData.conf",
    region = Region.EU_WEST_1,
    timeout = 5.minutes
  )

  IamUnrecognisedUsers.run(settings)
}
