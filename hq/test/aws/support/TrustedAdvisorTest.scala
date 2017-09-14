package aws.support

import com.amazonaws.services.support.model.{DescribeTrustedAdvisorChecksResult, TrustedAdvisorCheckDescription}
import org.scalatest.{FreeSpec, Matchers, OptionValues}


class TrustedAdvisorTest extends FreeSpec with Matchers with OptionValues {
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
        'id ("id"),
        'name ("name"),
        'description ("description"),
        'category ("category")
      )
    }
  }
}
