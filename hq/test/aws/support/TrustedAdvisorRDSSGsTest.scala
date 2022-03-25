package aws.support

import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import utils.attempt.AttemptValues

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class TrustedAdvisorRDSSGsTest extends AnyFreeSpec with Matchers with AttemptValues {
  "parseRDSSGDetail" - {
    val metadata = List("eu-west-1", "rds-sg-123456", "sg-12345a", "Yellow")
    val detail = new TrustedAdvisorResourceDetail()
      .withIsSuppressed(false)
      .withMetadata(metadata.asJava)
      .withRegion("eu-west-1")
      .withStatus("ok")
      .withResourceId("abcdefz")

    "works on example data" in {
      TrustedAdvisorRDSSGs.parseRDSSGDetail(detail).value() should have(
        Symbol("region") ("eu-west-1"),
        Symbol("rdsSgId") ("rds-sg-123456"),
        Symbol("ec2SGId") ("sg-12345a"),
        Symbol("alertLevel") ("Yellow"),
        Symbol("isSuppressed") (false)
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
      TrustedAdvisorRDSSGs.parseRDSSGDetail(badDetail).isFailedAttempt() shouldEqual true
    }
  }
}
