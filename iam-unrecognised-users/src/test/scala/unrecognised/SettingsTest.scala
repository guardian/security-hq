package unrecognised

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SettingsTest extends AnyFreeSpec with Matchers {

  private val requiredEnv = Map(
    "CONFIG_BUCKET" -> "dist-bucket",
    "CONFIG_KEY" -> "security/PROD/security-hq/security-hq.conf",
    "IAM_UNRECOGNISED_USER_S3_BUCKET" -> "audit-data-bucket",
    "IAM_UNRECOGNISED_USER_S3_KEY" -> "security/PROD/janus/data.json"
  )

  "fromEnvironment" - {
    // DRY_RUN must default to true so a mis-configured deployment can never deactivate real users.
    "defaults DRY_RUN to true when it is not set (fail safe)" in {
      Settings.fromEnvironment(requiredEnv).dryRun shouldBe true
    }

    "parses DRY_RUN=false as false" in {
      Settings.fromEnvironment(requiredEnv + ("DRY_RUN" -> "false")).dryRun shouldBe false
    }
  }
}
