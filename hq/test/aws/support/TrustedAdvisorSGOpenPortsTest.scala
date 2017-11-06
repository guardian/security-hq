package aws.support

import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt.AttemptValues

import scala.collection.JavaConverters._


class TrustedAdvisorSGOpenPortsTest extends FreeSpec with Matchers with AttemptValues {
  "parsing details" - {
    val metadata = List("eu-west-1", "launch-wizard-1", "sg-12345a (vpc-789abc)", "tcp", "Yellow", "22")
    val detail = new TrustedAdvisorResourceDetail()
      .withIsSuppressed(false)
      .withMetadata(metadata.asJava)
      .withRegion("eu-west-1")
      .withStatus("ok")
      .withResourceId("abcdefz")

    "works on example data" in {
      TrustedAdvisorSGOpenPorts.parseSGOpenPortsDetail(detail).value() should have(
        'region ("eu-west-1"),
        'name ("launch-wizard-1"),
        'id ("sg-12345a"),
        'vpcId ("vpc-789abc"),
        'protocol ("tcp"),
        'port ("22"),
        'alertLevel ("Yellow"),
        'isSuppressed (false)
      )
    }
  }

  "regex" - {
    import TrustedAdvisorSGOpenPorts.SGIds

    "works for valid data" in {
      "sg-12345a (vpc-789abc)" match {
        case SGIds(sgId, vpcId) =>
          sgId shouldEqual "sg-12345a"
          vpcId shouldEqual "vpc-789abc"
      }
    }
  }
}
