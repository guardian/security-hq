package config

import org.scalatest.EitherValues

import scala.concurrent.Await
import scala.concurrent.duration.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration

class ConfigTest extends AnyFreeSpec with Matchers with EitherValues {

  val configuration: Configuration = Configuration.from(
    Map(
      "trueValue" -> "true",
      "falseValue" -> "false",
      "TRUEValue" -> "TRUE",
      "FALSEValue" -> "FALSE",
      "rubbishValue" -> "rubbish"
    )
  )


  "Check we read the dryRun flag correctly from the config" - {

    "only exactly 'false' (ignoring case) is treated as false" - {
      "dryRun flag is 'false'" in {
        Config.getDryRun(configuration, "falseValue") shouldBe false
      }

      "dryRun flag is 'FALSE'" in {
        Config.getDryRun(configuration, "FALSEValue") shouldBe false
      }
    }

    "anything that isn't exactly 'false' is treated as true" - {
      "dryRun flag is 'true'" in {
        Config.getDryRun(configuration, "trueValue") shouldBe true
      }

      "dryRun flag is 'TRUE'" in {
        Config.getDryRun(configuration, "TRUEValue") shouldBe true
      }

      "dryRun flag is 'rubbish'" in {
        Config.getDryRun(configuration, "rubbishValue") shouldBe true
      }

      "dryRun flag is not present" in {
        Config.getDryRun(configuration, "missing") shouldBe true
      }
    }
  }
}