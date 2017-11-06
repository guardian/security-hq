package aws.support

import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import org.scalatest.{FreeSpec, Matchers, OptionValues}
import utils.attempt.AttemptValues

import scala.collection.JavaConverters._


class TrustedAdvisorRDSSGsTest extends FreeSpec with Matchers with AttemptValues {
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
        'region ("eu-west-1"),
        'rdsSgId ("rds-sg-123456"),
        'ec2SGId ("sg-12345a"),
        'alertLevel ("Yellow"),
        'isSuppressed (false)
      )
    }
  }
}
