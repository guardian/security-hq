package api

import model._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object Snyk {

  def getSnykOrganisations(token: SnykToken, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[WSResponse] = {
    println(token)
    val snykOrgUrl = "https://snyk.io/api/v1/orgs"
    val futureResponse = wsClient.url(snykOrgUrl)
      .addHttpHeaders("Authorization" -> s"token ${token.value}")
        .addQueryStringParameters(("apple", "orange"))
      .get
    Attempt.fromFuture(futureResponse) { case NonFatal(e) =>
      val failure = Failure(e.getMessage, "Could not read organisations from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
    }
  }

  def getProjects(token: SnykToken, organisations: List[SnykOrganisation], wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[(SnykOrganisation, WSResponse)]] = {
    Attempt.traverse(organisations) { organisation =>
      println(organisation)
      val snykProjectsUrl = s"https://snyk.io/api/v1/org/${organisation.id}/projects"
      val b = wsClient.url(snykProjectsUrl)
        .addHttpHeaders("Authorization" -> s"token ${token.value}")
        .get()
        .transform(response => (organisation, response), f => f )
      Attempt.fromFuture(b) {
        case NonFatal(e) =>
          val failure = Failure(e.getMessage, "Could not read projects from Snyk", 502, None, Some(e))
          FailedAttempt(failure)
        }
    }
  }

  def getProjectVulnerabilities(projects: List[SnykProject], token: SnykToken, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[WSResponse]] = {

    val projectVulnerabilityResponses = projects
      .map(project => {
        val snykProjectUrl = s"https://snyk.io/api/v1/org/${project.organisation.get.id}/project/${project.id}/issues"
        println(snykProjectUrl)
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
