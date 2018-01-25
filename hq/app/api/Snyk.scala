package api

import model._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object Snyk {

  def getSnykOrganisations(token: Token, wsClient: WSClient)(implicit ec:ExecutionContext): Attempt[WSResponse] = {

    val snykOrgUrl = "https://snyk.io/api/v1/orgs"

    val futureResponse = wsClient.url(snykOrgUrl)
      .addHttpHeaders("Authorization" -> s"token ${token.value}")
      .get

    Attempt.fromFuture(futureResponse) { case NonFatal(e) =>
      val failure = Failure(e.getMessage, "Could not read organisations from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
    }
  }

  def getProjects(token: Token, id: String, wsClient: WSClient)(implicit ec:ExecutionContext): Attempt[WSResponse] = {
    val snykProjectsUrl = s"https://snyk.io/api/v1/org/$id/projects"
    val a = wsClient.url(snykProjectsUrl)
        .addHttpHeaders("Authorization" -> s"token ${token.value}")
        .get()

    Attempt.fromFuture(a) { case NonFatal(e) =>
      val failure = Failure(e.getMessage, "Could not read projects from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
    }
  }

  def getProjectVulnerabilities(id: String, projects: List[SnykProject], token: Token, wsClient: WSClient)(implicit ec:ExecutionContext): Attempt[List[WSResponse]] = {
    val projectVulnerabilityResponses = projects
      .map(project => {
        val snykProjectUrl = s"https://snyk.io/api/v1/org/$id/project/${project.id}/issues"

        val projectIssuesFilter = Json.obj(
          "filters" -> Json.obj(
            "severity" -> JsArray(List(
              JsString("high"), JsString("medium"), JsString("low")
            )),
            "types" -> JsArray(List(
              JsString("vuln")
            )),
            "ignored" -> "false",
            "patched" -> "false"
          )
        )

        wsClient.url(snykProjectUrl)
          .addHttpHeaders("Authorization" -> s"token ${token.value}")
          .post(projectIssuesFilter)

      })
    Attempt.traverse(projectVulnerabilityResponses) {
      projectVulnerabilityResponse =>
        Attempt.fromFuture(projectVulnerabilityResponse) {
          case NonFatal(e) =>
            val failure = Failure(e.getMessage, "Could not read project vulnerabilities from Snyk", 502, None, Some(e))
            FailedAttempt(failure)
        }
    }
  }
}
