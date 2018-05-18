package logic

import org.scalatest.{FreeSpec, Matchers}


class DocumentUtilTest extends FreeSpec with Matchers {
  "replaceSnykSSOUrl" - {
    "replaces placeholder with provided string" in {
      val template =
        """|First line
           |  %SNYK_SSO_LINK%
           |Another line
           |""".stripMargin
      DocumentUtil.replaceSnykSSOUrl("snykSSOUrl")(template) should not include "%SNYK_SSO_LINK%"
    }

    "includes replacement string in result" in {
      val template =
        """|First line
           |  %SNYK_SSO_LINK%
           |Another line
           |""".stripMargin
      DocumentUtil.replaceSnykSSOUrl("snykSSOUrl")(template) should include("snykSSOUrl")
    }
  }
}
