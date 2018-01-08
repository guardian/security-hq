package logic

import model._
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt.{Attempt, AttemptValues}
import scala.concurrent.ExecutionContext.Implicits.global

class SnykDisplayTest extends FreeSpec with Matchers with AttemptValues {

  "find organisationId" in {
    val organisationId = SnykDisplay.getOrganisationId(SnykDisplayTest.mockGoodOrganisationResponse, new Organisation("guardian"))
    organisationId.value() shouldBe "1111111111"
  }

  "fail to find organisationId" in {
    val organisationId = SnykDisplay.getOrganisationId(SnykDisplayTest.mockBadResponse, new Organisation("guardian"))
    organisationId.isFailedAttempt shouldBe true
    organisationId.getFailedAttempt.get.failures.head.friendlyMessage shouldBe "Could not read Snyk response"
  }

  "find project id list" in {
    val projects = SnykDisplay.getProjectIdList(SnykDisplayTest.mockGoodProjectResponse)
    projects.value().find(p => p.name == "project1").isEmpty shouldBe false
    projects.value().find(p => p.name == "project2").isEmpty shouldBe false
  }

  "fail to find project id list" in {
    val projects = SnykDisplay.getProjectIdList(SnykDisplayTest.mockBadResponse)
    projects.isFailedAttempt shouldBe true
    projects.getFailedAttempt.get.failures.head.friendlyMessage shouldBe "Could not read Snyk response"
  }

  "find empty vulnerability list" in {
    val projects = SnykDisplay.parseProjectVulnerabilities(List(SnykDisplayTest.mockGoodAndNotVulnerableResponse))
    projects.value().head.ok shouldBe true
  }

  "find non-empty vulnerability list" in {
    val projects = SnykDisplay.parseProjectVulnerabilities(List(SnykDisplayTest.mockGoodButVulnerableResponse))
    projects.value().head.ok shouldBe false
    projects.value().head.high shouldBe 1
    projects.value().head.medium shouldBe 0
    projects.value().head.low shouldBe 0
  }

  "fail to find vulnerability list" in {
    val projects = SnykDisplay.parseProjectVulnerabilities(List(SnykDisplayTest.mockBadResponse))
    projects.isFailedAttempt() shouldBe true
    projects.getFailedAttempt.get.failures.head.friendlyMessage shouldBe "Could not read Snyk response"
  }

  "label projects" in {
    val results = SnykDisplay.labelProjects(SnykDisplayTest.goodProjects, SnykDisplayTest.goodVulnerabilities)
    results.head.id shouldBe "id1"
    results.head.name shouldBe "name1"
    results.tail.head.id shouldBe "id2"
    results.tail.head.name shouldBe "name2"
  }

}

object SnykDisplayTest  {

  val mockBadResponse = s"""{\"banana\": \"apple\"}"""
  val mockGoodOrganisationResponse =
    s"""
       |{\"orgs\":
       |[
       |{ \"name\": \"guardian\", \"id\": \"1111111111\" },
       |{ \"name\": \"nottheguardian\", \"id\": \"9999999999\" }
       |]
       |}""".stripMargin

  val mockGoodProjectResponse =
    s"""
       |{
       |  "org": {
       |    "name": "guardian",
       |    "id": "1111111111"
       |  },
       |  "projects": [
       |    {
       |      "name": "project1",
       |      "id": "2222222222"
       |    },
       |    {
       |      "name": "project2",
       |      "id": "3333333333"
       |    }
       |  ]
       |}
     """.stripMargin

  val mockGoodAndNotVulnerableResponse =
    s"""
       |{
       |  "ok": true,
       |  "issues": {
       |    "vulnerabilities": [],
       |    "licenses": []
       |  },
       |  "dependencyCount": 0,
       |  "packageManager": "sbt"
       |}
     """.stripMargin

  val mockGoodButVulnerableResponse =
    s"""
       |{
       |  "ok": false,
       |  "issues": {
       |    "vulnerabilities": [
       |      {
       |        "id": "4444444444",
       |        "url": "https://snyk.io/vuln/4444444444",
       |        "title": "The Title",
       |        "type": "vuln",
       |        "description": "The description",
       |        "from": [
       |          "from1",
       |          "from2",
       |          "from3"
       |        ],
       |        "package": "The package",
       |        "version": "The version",
       |        "severity": "high",
       |        "language": "The language",
       |        "packageManager": "the package manager",
       |        "semver": {
       |          "unaffected": ">=the version",
       |          "vulnerable": "<the version"
       |        },
       |        "publicationTime": "2015-11-06T02:09:36.182Z",
       |        "disclosureTime": "2015-11-03T07:15:12.900Z",
       |        "isUpgradable": true,
       |        "isPatchable": true,
       |        "identifiers": {
       |          "CVE": [],
       |          "CWE": [],
       |          "NSP": 57,
       |          "ALTERNATIVE": [
       |            "5555555555"
       |          ]
       |        },
       |        "credit": [
       |          "Mickey Mouse"
       |        ],
       |        "CVSSv3": "CVSS:3.0/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:H/A:N",
       |        "cvssScore": 7.5,
       |        "patches": [
       |          {
       |            "id": "6666666666",
       |            "urls": [
       |              "https://example.com/6666666666.patch"
       |            ],
       |            "version": "<the version >=minversion",
       |            "comments": [
       |              "https://example.com/7777777777.patch"
       |            ],
       |            "modificationTime": "2015-11-17T09:29:10.000Z"
       |          }
       |        ],
       |        "upgradePath": [
       |          true,
       |          "the first upgrade",
       |          "the second upgrade"
       |        ]
       |      }
       |    ],
       |    "licenses": []
       |  },
       |  "dependencyCount": 0,
       |  "packageManager": "the package manager"
       |}
     """.stripMargin

  val goodProjects = List(
    SnykProject("name1", "id1"),
    SnykProject("name2", "id2")
  )
  val goodVulnerabilities = List(
    SnykProjectIssues("fake name1", "fake id1", false, List[SnykIssue]()),
    SnykProjectIssues("fake name2", "fake id2", false, List[SnykIssue]())
  )


}

