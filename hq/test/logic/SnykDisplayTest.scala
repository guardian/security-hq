package logic

import model.{SnykIssue, SnykOrganisation, _}
import org.joda.time.DateTime
import utils.attempt.AttemptValues

import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SnykDisplayTest extends AnyFreeSpec with Matchers with AttemptValues {

  private def readFile(filename:String) = Source.fromResource(s"logic/SnykDisplayTestResources/$filename.json").getLines().mkString

  private val mockBadResponseWithMessage = readFile("badResponseWithMessage")
  private val mockBadResponseWithoutMessage = readFile("badResponseWithoutMessage")

  "parse single organisation" - {
    "null group" in {
      val organisationAttempt = SnykDisplay.parseJsonToOrganisationList(readFile("nullgroup"))
      organisationAttempt.value() shouldBe List(SnykOrganisation("guardian-org-1", "id1", None))
    }

    "no group" in {
      val organisationAttempt = SnykDisplay.parseJsonToOrganisationList(readFile("nogroup"))
      organisationAttempt.value() shouldBe List(SnykOrganisation("guardian-org-1", "id1", None))
    }

    "real group" in {
      val organisationAttempt = SnykDisplay.parseJsonToOrganisationList(readFile("realgroup"))
      organisationAttempt.value() shouldBe List(SnykOrganisation("guardian-org-1", "id1", Some(SnykGroup("guardian-org-2", "id2"))))
    }
  }

  "find organisations" in {
    val organisation = SnykDisplay.parseOrganisations(readFile("goodOrganisationResponse"), SnykGroupId("id0"))
    organisation.value() shouldBe List(
      SnykOrganisation("guardian-org-1", "id1", Some(SnykGroup("guardian", "id0"))),
      SnykOrganisation("guardian-org-2", "id2", Some(SnykGroup("guardian", "id0")))
    )
  }

  "fail to find organisation" - {
    "bad response" in {
      val organisationId = SnykDisplay.parseOrganisations(s"""response is not json!""", SnykGroupId("guardian"))
      organisationId.isFailedAttempt() shouldBe true
      organisationId.getFailedAttempt().failures.head.friendlyMessage shouldBe "Could not read Snyk response (response is not json!)"
    }
    "message received in response" in {
      val organisationId = SnykDisplay.parseOrganisations(mockBadResponseWithMessage, SnykGroupId("guardian"))
      organisationId.isFailedAttempt() shouldBe true
      organisationId.getFailedAttempt().failures.head.friendlyMessage shouldBe "Could not read Snyk response (some nice error)"
    }
    "message not received in response" in {
      val organisationId = SnykDisplay.parseOrganisations(mockBadResponseWithoutMessage, SnykGroupId("guardian"))
      organisationId.isFailedAttempt() shouldBe true
      organisationId.getFailedAttempt().failures.head.friendlyMessage shouldBe """Could not read Snyk response ({"toughluck": "no use"})"""
    }
  }

  "find projects" - {
    val mockGoodProjectResponse = readFile("goodProjectResponse")
    val projects = SnykDisplay.parseProjectResponses(
      List(
        (
          SnykOrganisation("1111111111", "1111111111", None),
          mockGoodProjectResponse
        )
      )
    )
    "find project 1 in project list" in {
      projects.value().head._2.exists(p => p.name == "project1") shouldBe true
    }
    "find project 2 in project list" in {
      projects.value().head._2.exists(p => p.name == "project2") shouldBe true
    }
  }

  val dummyOrg = SnykOrganisation("dummy", "dummy", None)
  "fail to find project list" - {
    "bad response" in {
      val projects = SnykDisplay.parseProjectResponses(List((dummyOrg, s"""response is not json!""")))
      projects.isFailedAttempt() shouldBe true
      projects.getFailedAttempt().failures.head.friendlyMessage shouldBe "Could not read Snyk response (response is not json!)"
    }
    "fail to find project id list (nice) - fails" in {
      val projects = SnykDisplay.parseProjectResponses(List((dummyOrg, mockBadResponseWithMessage)))
      projects.isFailedAttempt() shouldBe true
    }
    "fail to find project id list (nice) - has message" in {
      val projects = SnykDisplay.parseProjectResponses(List((dummyOrg, mockBadResponseWithMessage)))
      projects.getFailedAttempt().failures.head.friendlyMessage shouldBe "Could not read Snyk response (some nice error)"
    }
    "fail to find project id list (not nice) - fails" in {
      val projects = SnykDisplay.parseProjectResponses(List((dummyOrg, mockBadResponseWithoutMessage)))
      projects.isFailedAttempt() shouldBe true
    }
    "fail to find project id list (not nice) - has message" in {
      val projects = SnykDisplay.parseProjectResponses(List((dummyOrg, mockBadResponseWithoutMessage)))
      projects.getFailedAttempt().failures.head.friendlyMessage shouldBe """Could not read Snyk response ({"toughluck": "no use"})"""
    }
  }

  "Parsing results for organisation vulnerabilities" - {
    "find no results for an org with no vulnerabilities" in {
      val organisations = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, readFile("goodAndNotVulnerableResponse"))))
      organisations.value().head.projectIssues shouldBe empty
    }

    val mockGoodButVulnerableResponse = readFile("goodButVulnerableResponse")

    "find high count for non-empty list" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, mockGoodButVulnerableResponse)))
      projects.value().head.high shouldBe 1
    }
    "find medium count for non-empty list" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, mockGoodButVulnerableResponse)))
      projects.value().head.medium shouldBe 0
    }
    "find low count for non-empty list" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, mockGoodButVulnerableResponse)))
      projects.value().head.low shouldBe 0
    }
  }

  "fail to find vulnerability list" - {
    "bad response" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, """response is not json!""")))
      projects.isFailedAttempt() shouldBe true
      projects.getFailedAttempt().failures.head.friendlyMessage shouldBe "Could not read Snyk response (response is not json!)"
    }
    "fail to find vulnerability list with error message - fails" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, mockBadResponseWithMessage)))
      projects.isFailedAttempt() shouldBe true
    }
    "fail to find vulnerability list with error message - has message" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, mockBadResponseWithMessage)))
      projects.getFailedAttempt().failures.head.friendlyMessage shouldBe "Could not read Snyk response (some nice error)"
    }
    "fail to find vulnerability list without error message - fails" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, mockBadResponseWithoutMessage)))
      projects.isFailedAttempt() shouldBe true
    }
    "fail to find vulnerability list without error message - has message" in {
      val projects = SnykDisplay.parseOrganisationVulnerabilities(List((dummyOrg, mockBadResponseWithoutMessage)))
      projects.getFailedAttempt().failures.head.friendlyMessage shouldBe """Could not read Snyk response ({"toughluck": "no use"})"""
    }
  }

  "sort orgs" - {
    "All equal except number of high risk issues" in {
      SnykDisplay.sortOrgs(
        List(
          SnykOrganisationIssues(
            SnykOrganisation("org", "1", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low"))
                )
              )
            )
          ),
          SnykOrganisationIssues(
            SnykOrganisation("org", "2", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "2", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "4", "low"))
                )
              )
            )
          )
        )
      ).map(spi => spi.organisation.id) shouldBe List[String]("2", "1")
    }

    "All equal except number of medium risk issues" in {
      SnykDisplay.sortOrgs(
        List(
          SnykOrganisationIssues(
            SnykOrganisation("org", "1", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low"))
                )
              )
            )
          ),
          SnykOrganisationIssues(
            SnykOrganisation("org", "2", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "2", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "4", "low"))
                )
              )
            )
          )
        )
      ).map(spi => spi.organisation.id) shouldBe List[String]("2", "1")
    }

    "All equal except number of low risk issues" in {
      SnykDisplay.sortOrgs(
        List(
          SnykOrganisationIssues(
            SnykOrganisation("org", "1", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low"))
                )
              )
            )
          ),
          SnykOrganisationIssues(
            SnykOrganisation("org", "2", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "2", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "4", "low"))
                )
              )
            )
          )
        )
      ).map(spi => spi.organisation.id) shouldBe List[String]("2", "1")
    }

    "All equal except org name" in {
      SnykDisplay.sortOrgs(
        List(
          SnykOrganisationIssues(
            SnykOrganisation("orgB", "1", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low"))
                )
              )
            )
          ),
          SnykOrganisationIssues(
            SnykOrganisation("orgA", "2", None),
            List(
              SnykProjectIssues(
                Some(SnykProject("X", "a", "url")),
                List(
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "2", "medium")),
                  SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low"))
                )
              )
            )
          )
        )
      ).map(spi => spi.organisation.id) shouldBe List[String]("2", "1")
    }
  }

  "sort projects" - {
    "All equal except number of high risk issues" in {
      SnykDisplay.sortProjects(
        List(
          SnykProjectIssues(
            Some(SnykProject("X", "b", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low"))
            )
          ),
          SnykProjectIssues(
            Some(SnykProject("X", "a", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "3", "low"))
            )
          )
        )
      ).map(spi => spi.project.get.id) shouldBe List[String]("a", "b")
    }
    "All equal except number of medium risk issues" in {
      SnykDisplay.sortProjects(
        List(
          SnykProjectIssues(
            Some(SnykProject("X", "b", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue3", "3", "low"))
            )
          ),
          SnykProjectIssues(
            Some(SnykProject("X", "a", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue3", "3", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue4", "4", "low"))
            )
          )
        )
      ).map(spi => spi.project.get.id) shouldBe List[String]("a", "b")
    }
    "All equal except number of low risk issues" in {
      SnykDisplay.sortProjects(
        List(
          SnykProjectIssues(
            Some(SnykProject("X", "b", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue3", "3", "low"))
            )
          ),
          SnykProjectIssues(
            Some(SnykProject("X", "a", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue3", "3", "low")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue4", "4", "low"))
            )
          )
        )
      ).map(spi => spi.project.get.id) shouldBe List[String]("a", "b")
    }
    "All equal except name" in {
      SnykDisplay.sortProjects(
        List(
          SnykProjectIssues(
            Some(SnykProject("Y", "b", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue3", "3", "low"))
            )
          ),
          SnykProjectIssues(
            Some(SnykProject("X", "a", "url")),
            List(
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue1", "1", "high")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue2", "2", "medium")),
              SnykProjectIssue(None, DateTime.now(), SnykIssue("Issue3", "3", "low"))
            )
          )
        )
      ).map(spi => spi.project.get.id) shouldBe List[String]("a", "b")
    }
  }

  "Determining long-lived issues" - {
    "find only old-enough high severity issues" in {
      val vulns = SnykDisplay.longLivedHighVulnerabilities(
        SnykOrganisationIssues(dummyOrg,
          List(
            SnykProjectIssues(None,
              List(
                SnykProjectIssue(None,
                  DateTime.now().minusDays(365),
                  SnykIssue("an old vulnerability", "1", "high")
                ),
                SnykProjectIssue(None,
                  DateTime.now().minusDays(1),
                  SnykIssue("a new vulnerability", "2", "high")
                ),
                SnykProjectIssue(None,
                  DateTime.now().minusDays(365),
                  SnykIssue("an old low-severity vulnerability", "3", "low")
                ),
              )
            )
          )
        )
      )
      vulns should have length 1
      vulns.head.issue should have(Symbol("id")("1"))
    }

    "find only old-enough critical severity issues" in {
      val vulns = SnykDisplay.longLivedCriticalVulnerabilities(
        SnykOrganisationIssues(dummyOrg,
          List(
            SnykProjectIssues(None,
              List(
                SnykProjectIssue(None,
                  DateTime.now().minusDays(365),
                  SnykIssue("an old vulnerability", "1", "critical")
                ),
                SnykProjectIssue(None,
                  DateTime.now().minusDays(1),
                  SnykIssue("a new vulnerability", "2", "critical")
                ),
                SnykProjectIssue(None,
                  DateTime.now().minusDays(365),
                  SnykIssue("an old low-severity vulnerability", "3", "high")
                ),
              )
            )
          )
        )
      )
      vulns should have length 1
      vulns.head.issue should have(Symbol("id")("1"))
    }
  }

}
