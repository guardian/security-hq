
import com.amazonaws.services.lambda.runtime.events.ConfigEvent
import com.gu.hq.lambda.ConfigEventLogic.logger
import com.gu.hq.lambda.model
import com.gu.hq.lambda.model.{InvokingEvent, JSON, SGConfiguration}
import org.scalatest.{FreeSpec, Matchers}
import play.api.libs.json.{JsValue, Json, Reads}
import JSON._

import scala.io.Source

class JSONTest extends FreeSpec with Matchers {

  private def readFile(filename: String) = Source.fromResource(s"$filename.json").getLines.mkString

  "parse config event" - {
    "Full Value" in {
      val x = readFile("config_event")
      val y = Json.parse(x).validate[InvokingEvent].fold(a => None, Some(_))
      y.isDefined shouldBe (true)
      val z = y.get.configurationItem.get.configuration.validate[SGConfiguration].fold(a => None, Some(_))
      z.isDefined shouldBe (true)
    }

    "Missing ipPermissionsEgress toPort Value" in {
      val x = readFile("config_event2")
      val y = Json.parse(x).validate[InvokingEvent].fold(a => None, Some(_))
      y.isDefined shouldBe (true)
      val z = y.get.configurationItem.get.configuration.validate[SGConfiguration].fold(a => None, Some(_))
      z.isDefined shouldBe (true)
    }
  }

}