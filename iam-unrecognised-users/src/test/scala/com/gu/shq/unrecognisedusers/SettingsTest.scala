package com.gu.shq.unrecognisedusers

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class SettingsTest extends AnyFreeSpec with Matchers {

  private val requiredEnv = Map(
    "CONFIG_BUCKET" -> "dist-bucket",
    "CONFIG_KEY" -> "security/PROD/security-hq/security-hq.conf",
    "IAM_UNRECOGNISED_USER_S3_BUCKET" -> "audit-data-bucket",
    "IAM_UNRECOGNISED_USER_S3_KEY" -> "security/PROD/janus/data.json"
  )

  private val timeout = 5.minutes

  "fromEnvironment" - {
    // DRY_RUN must default to true so a mis-configured deployment can never deactivate real users.
    "defaults DRY_RUN to true when it is not set (fail safe)" in {
      Settings.fromEnvironment(requiredEnv, timeout).dryRun shouldBe true
    }

    "parses DRY_RUN=false as false" in {
      Settings.fromEnvironment(requiredEnv + ("DRY_RUN" -> "false"), timeout).dryRun shouldBe false
    }
  }
}
