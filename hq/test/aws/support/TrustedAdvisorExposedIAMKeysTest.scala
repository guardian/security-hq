package aws.support

import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConverters._


class TrustedAdvisorExposedIAMKeysTest extends FreeSpec with Matchers {
  "parseRDSSGDetail" - {
    val metadata = List("key-id", "username", "fraud-type", "case-id", "last-updated", "location", "deadline", "usage")
    val detail = new TrustedAdvisorResourceDetail()
      .withIsSuppressed(false)
      .withMetadata(metadata.asJava)
      .withRegion("eu-west-1")
      .withStatus("ok")
      .withResourceId("abcdefz")

    "works on example data" in {
      TrustedAdvisorExposedIAMKeys.parseExposedIamKeyDetail(detail) should have(
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
  }
}
