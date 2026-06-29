package aws.support

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.support.model.{DescribeTrustedAdvisorChecksResponse, TrustedAdvisorCheckDescription}


class TrustedAdvisorTest extends AnyFreeSpec with Matchers with OptionValues {
  "parseTrustedAdvisorChecksResult" - {
    val response = DescribeTrustedAdvisorChecksResponse.builder.checks(
      TrustedAdvisorCheckDescription.builder
        .id("id")
        .name("name")
        .description("description")
        .category("category")
        .build()
    ).build()

    "should parse a single result" in {
      TrustedAdvisor.parseTrustedAdvisorChecksResponse(response).length shouldEqual 1
    }

    "should correctly extract the fields" in {
      val record = TrustedAdvisor.parseTrustedAdvisorChecksResponse(response).headOption.value
      record should have (
        Symbol("id") ("id"),
        Symbol("name") ("name"),
        Symbol("description") ("description"),
        Symbol("category") ("category")
      )
    }
  }
}
