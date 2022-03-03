package com.gu.hq.lambda.model

import com.gu.hq.lambda.model.JSON._
import org.scalatest.OptionValues
import play.api.libs.json.Json

import scala.io.Source
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class JSONTest extends AnyFreeSpec with Matchers with OptionValues {
  "parse config event" - {
    "can parse an event triggered by a change" - {
      val irrelevantEventJson = loadJsonResource("config_event_with_irrelevant_update")
      val relevantEventJson = loadJsonResource("config_event_with_relevant_update")

      "parse irrelevant event JSON" in {
        Json.parse(irrelevantEventJson).validate[InvokingEvent].isSuccess shouldBe true
      }

      "parse relevant event JSON" in {
        Json.parse(relevantEventJson).validate[InvokingEvent].isSuccess shouldBe true
      }

      "can parse configuration JSON out of the configuration item" in {
        val event = Json.parse(irrelevantEventJson).validate[InvokingEvent].asOpt.value
        val configurationItem = event.configurationItem.value
        configurationItem.configuration.validate[SGConfiguration].isSuccess shouldBe true
      }
    }

    "can parse an event triggered on a schedule (no change)" - {
      val eventJson = loadJsonResource("config_event_no_change")

      "parses event JSON" in {
        Json.parse(eventJson).validate[InvokingEvent].isSuccess shouldBe true
      }

      "can parse configuration JSON out of the configuration item" in {
        val event = Json.parse(eventJson).validate[InvokingEvent].asOpt.value
        val configurationItem = event.configurationItem.value
        configurationItem.configuration.validate[SGConfiguration].isSuccess shouldBe true
      }
    }
  }

  private def loadJsonResource(filename: String) = Source.fromResource(s"$filename.json").getLines().mkString
}
