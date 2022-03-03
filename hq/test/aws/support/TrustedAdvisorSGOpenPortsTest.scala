package aws.support

import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model._
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import utils.attempt.AttemptValues

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global


class TrustedAdvisorSGOpenPortsTest extends AnyFreeSpec with Matchers with AttemptValues with ScalaCheckPropertyChecks  {
  "parsing details" - {
    "works on example data" in {
      val metadata = List("eu-west-1", "launch-wizard-1", "sg-12345a (vpc-789abc)", "tcp", "Yellow", "22")
      val detail = new TrustedAdvisorResourceDetail()
        .withIsSuppressed(false)
        .withMetadata(metadata.asJava)
        .withRegion("eu-west-1")
        .withStatus("ok")
        .withResourceId("abcdefz")
      TrustedAdvisorSGOpenPorts.parseSGOpenPortsDetail(detail).value() should have(
        'region ("eu-west-1"),
        'name ("launch-wizard-1"),
        'id ("sg-12345a"),
        'vpcId ("vpc-789abc"),
        'protocol ("tcp"),
        'port ("22"),
        'alertLevel ("Yellow"),
        'isSuppressed (false),
        'stackId (None),
        'stackName (None),
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
      val vpcId = TrustedAdvisorSGOpenPorts.parseSGOpenPortsDetail(detailWithoutVpc).value().vpcId
      vpcId shouldEqual "EC2 classic"
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

  "sorting" - {

    "security flags by port priority" in {
      val sgs: Gen[SGOpenPortsDetail] = for {
        status <- Gen.oneOf("Ok", "Warning", "Error")
        name <- Gen.alphaStr
        id <- Gen.alphaStr
        vpcId <- Gen.alphaStr
        port <- Gen.oneOf(TrustedAdvisor.portPriorityMap.flatMap(_._2) )
        item = SGOpenPortsDetail(status, "eu-west-1", name.take(4), id.take(4), vpcId.take(4), "tcp", port.toString, "Yellow", false )

      } yield item

      forAll(Gen.listOf(sgs)) { detail =>
        val sortedResult = TrustedAdvisor.sortSecurityFlags(detail)
        val expected = sortedResult.sortWith {
          case  (s1, s2) =>  TrustedAdvisor.findPortPriorityIndex(s1.port).getOrElse(999) < TrustedAdvisor.findPortPriorityIndex(s2.port).getOrElse(999)
        }
        sortedResult should be (expected)
      }
    }

    "handle range ports" in {
      val portRange = "3304-3307"
      TrustedAdvisor.findPortPriorityIndex(portRange) shouldBe Some(2)
    }

    "handle simple port" in {
      TrustedAdvisor.indexedPortMap.foreach { case ((k, ports), idx) =>
        ports.foreach { port =>
          TrustedAdvisor.findPortPriorityIndex(port.toString) shouldBe Some(idx)
        }
      }
    }

    "handle null in port" in {
      TrustedAdvisor.findPortPriorityIndex(null) shouldBe None
    }
    "handle empty string in port" in {
      TrustedAdvisor.findPortPriorityIndex(null) shouldBe None
    }


    "security flags by alert level " in {
      val  alertLevelMapping = Map("Red" -> 0, "Yellow" -> 1, "Green" -> 2)
      val sgsOpenPorts = for {
        status <- Gen.oneOf("Ok", "Warning", "Error")
        alertLevel <- Gen.oneOf("Yellow", "Red", "Green")
        name <- Gen.alphaStr
        id <- Gen.alphaStr
        vpcId <- Gen.alphaStr
        port <- Gen.oneOf( 0 to 65000)
      } yield SGOpenPortsDetail(status, "eu-west-1", name.take(4), id.take(4), vpcId.take(4), "tcp", port.toString, alertLevel, false )

      forAll(Gen.listOf(sgsOpenPorts)) { sgs =>
        val sortedResult = TrustedAdvisor.sortSecurityFlags(sgs)
        sortedResult should be (sortedResult.sortWith{ case  (s1, s2) =>  alertLevelMapping.getOrElse(s1.alertLevel, 2) < alertLevelMapping.getOrElse(s2.alertLevel, 2)  })
      }
    }

    "RDS security flags by alert level " in {
      val rdsSgs = for {
        alertLevel <- Gen.oneOf("Yellow", "Red", "Green")
        ec2SgId <- Gen.alphaStr
        rdsSgId <- Gen.alphaStr
      } yield RDSSGsDetail("eu-west-1", rdsSgId.take(4), ec2SgId.take(4), alertLevel, false )

      forAll(Gen.listOf(rdsSgs)) { sgs =>
        val sortedResult = TrustedAdvisor.sortSecurityFlags(sgs)
        sortedResult should be (sortedResult.sortWith{ case  (s1, s2) =>  TrustedAdvisor.alertLevelMapping.getOrElse(s1.alertLevel, 2) < TrustedAdvisor.alertLevelMapping.getOrElse(s2.alertLevel, 2)  })
      }
    }
  }
}
