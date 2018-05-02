package com.gu.hq

import com.gu.hq.Events.{NotRelevant, Relevant}
import com.gu.hq.lambda.model.InvokingEvent
import com.gu.hq.lambda.model.JSON._
import org.scalatest.{FreeSpec, Matchers}
import play.api.libs.json.Json

import scala.io.Source

class EventsTest extends FreeSpec with Matchers {

  "parse config event" - {
    def loadJsonResource(filename: String) = Source.fromResource(s"$filename.json").getLines.mkString
    "can parse an event triggered by a change" - {
      "irrelevant event is irrelevant" in {
        val irrelevantEventJson = loadJsonResource("config_event_with_irrelevant_update")
        Events.relevance(Json.parse(irrelevantEventJson).validate[InvokingEvent].get) should be (NotRelevant)
      }

      "relevant event is relevant" in {
        val relevantEventJson = loadJsonResource("config_event_with_relevant_update")
        Events.relevance(Json.parse(relevantEventJson).validate[InvokingEvent].get) shouldBe Relevant
      }
    }
  }

}
