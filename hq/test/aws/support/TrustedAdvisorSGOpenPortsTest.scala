package aws.support

import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt.AttemptValues

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global


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

    "works on example data if there is no vpc" in {
      val metadataWithoutVpc = List("eu-west-1", "launch-wizard-1", "sg-12345a", "tcp", "Yellow", "22")
      val detailWithoutVpc = new TrustedAdvisorResourceDetail()
        .withIsSuppressed(false)
        .withMetadata(metadataWithoutVpc.asJava)
        .withRegion("eu-west-1")
        .withStatus("ok")
        .withResourceId("abcdefz")
      TrustedAdvisorSGOpenPorts.parseSGOpenPortsDetail(detailWithoutVpc).value().vpcId should be(
        "EC2 classic"
      )
    }

    "returns a failure if it cannot parse the result" in {
      val badMetadata: List[String] = Nil
      val badDetail = new TrustedAdvisorResourceDetail()
        .withIsSuppressed(false)
        .withMetadata(badMetadata.asJava)
        .withRegion("eu-west-1")
        .withStatus("ok")
        .withResourceId("abcdefz")
      TrustedAdvisorSGOpenPorts.parseSGOpenPortsDetail(badDetail).isFailedAttempt() shouldEqual true
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
