package aws.support

import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import utils.attempt.AttemptValues

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class TrustedAdvisorExposedIAMKeysTest extends AnyFreeSpec with Matchers with AttemptValues {
  "parseRDSSGDetail" - {
    val metadata = List("key-id", "username", "fraud-type", "case-id", "last-updated", "location", "deadline", "usage")
    val detail = new TrustedAdvisorResourceDetail()
      .withIsSuppressed(false)
      .withMetadata(metadata.asJava)
      .withRegion("eu-west-1")
      .withStatus("ok")
      .withResourceId("abcdefz")

    "works on example data" in {
      TrustedAdvisorExposedIAMKeys.parseExposedIamKeyDetail(detail).value() should have(
        'keyId ("key-id"),
        'username ("username"),
        'fraudType ("fraud-type"),
        'caseId ("case-id"),
        'updated ("last-updated"),
        'location ("location"),
        'deadline ("deadline"),
        'usage ("usage")
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
      TrustedAdvisorExposedIAMKeys.parseExposedIamKeyDetail(badDetail).isFailedAttempt() shouldEqual true
    }
  }
}
