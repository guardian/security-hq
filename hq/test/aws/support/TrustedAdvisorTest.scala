package aws.support

import com.amazonaws.services.support.model.{DescribeTrustedAdvisorChecksResult, TrustedAdvisorCheckDescription}
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class TrustedAdvisorTest extends AnyFreeSpec with Matchers with OptionValues {
  "parseTrustedAdvisorChecksResult" - {
    val result = new DescribeTrustedAdvisorChecksResult().withChecks(
      new TrustedAdvisorCheckDescription()
        .withId("id")
        .withName("name")
        .withDescription("description")
        .withCategory("category")
    )

    "should parse a single result" in {
      TrustedAdvisor.parseTrustedAdvisorChecksResult(result).length shouldEqual 1
    }

    "should correctly extract the fields" in {
      val record = TrustedAdvisor.parseTrustedAdvisorChecksResult(result).headOption.value
      record should have (
        Symbol("id") ("id"),
        Symbol("name") ("name"),
        Symbol("description") ("description"),
        Symbol("category") ("category")
      )
    }
  }
}
