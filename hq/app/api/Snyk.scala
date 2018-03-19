package api

import model._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object Snyk {

  def snykRequest(token:SnykToken, url:String, wsClient: WSClient) = 
    wsClient.url(url).addHttpHeaders("Authorization" -> s"token ${token.value}")

  def getSnykOrganisations(token: SnykToken, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[WSResponse] = {
    val snykOrgUrl = "https://snyk.io/api/v1/orgs"
    val futureResponse = snykRequest(token, snykOrgUrl, wsClient).get
    handleFuture(futureResponse, "organisation")
  }

  def getProjects(token: SnykToken, organisations: List[SnykOrganisation], wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[(SnykOrganisation, String)]] = {
    Attempt.traverse(organisations) { organisation =>
      val snykProjectsUrl = s"https://snyk.io/api/v1/org/${organisation.id}/projects"
      val futureResponse = snykRequest(token, snykProjectsUrl, wsClient).get
        .transform(response => (organisation, response.body), f => f )
      handleFuture(futureResponse, "project")
    }
  }

  def getProjectVulnerabilities(projects: List[SnykProject], token: SnykToken, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[WSResponse]] = {

    val projectVulnerabilityResponses = projects
      .map(project => {
        val snykProjectUrl = s"https://snyk.io/api/v1/org/${project.organisation.get.id}/project/${project.id}/issues"
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
        snykRequest(token, snykProjectUrl, wsClient).post(projectIssuesFilter)
      })
    Attempt.traverse(projectVulnerabilityResponses) {
      projectVulnerabilityResponse => handleFuture(projectVulnerabilityResponse, "project vulnerabilities")
    }
  }

  def handleFuture[A](future: Future[A], label: String)(implicit ec: ExecutionContext) = Attempt.fromFuture(future) {
    case NonFatal(e) =>
      val failure = Failure(e.getMessage, s"Could not read ${label} from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
  }
}
