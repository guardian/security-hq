package aws.support

import utils.attempt.AttemptValues

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.support.model.TrustedAdvisorResourceDetail


class TrustedAdvisorExposedIAMKeysTest extends AnyFreeSpec with Matchers with AttemptValues {
  "parseRDSSGDetail" - {
    val metadata = List("key-id", "username", "fraud-type", "case-id", "last-updated", "location", "deadline", "usage")
    val detail = TrustedAdvisorResourceDetail.builder
      .isSuppressed(false)
      .metadata(metadata.asJava)
      .region("eu-west-1")
      .status("ok")
      .resourceId("abcdefz")
      .build()

    "works on example data" in {
      TrustedAdvisorExposedIAMKeys.parseExposedIamKeyDetail(detail).value() should have(
        Symbol("keyId") ("key-id"),
        Symbol("username") ("username"),
        Symbol("fraudType") ("fraud-type"),
        Symbol("caseId") ("case-id"),
        Symbol("updated") ("last-updated"),
        Symbol("location") ("location"),
        Symbol("deadline") ("deadline"),
        Symbol("usage") ("usage")
      )
    }

    "returns a failure if it cannot parse the result" in {
      val badMetadata: List[String] = Nil
      val badDetail = TrustedAdvisorResourceDetail.builder
        .isSuppressed(false)
        .metadata(badMetadata.asJava)
        .region("eu-west-1")
        .status("ok")
        .resourceId("abcdefz")
        .build()
      TrustedAdvisorExposedIAMKeys.parseExposedIamKeyDetail(badDetail).isFailedAttempt() shouldEqual true
    }
  }
}
