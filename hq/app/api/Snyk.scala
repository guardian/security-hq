package api

import logic.SnykDisplay
import model._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import utils.attempt.{Attempt, FailedAttempt, Failure}
import com.gu.configraun.models.Configuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object Snyk {

  private def snykRequest(token:SnykToken, url:String, wsClient: WSClient) =
    wsClient.url(url).addHttpHeaders("Authorization" -> s"token ${token.value}")

  def getSnykOrganisations(token: SnykToken, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[WSResponse] = {
    val snykOrgUrl = "https://snyk.io/api/v1/orgs"
    val futureResponse = snykRequest(token, snykOrgUrl, wsClient).get
    handleFuture(futureResponse, "organisation")
  }

  def getProjects(token: SnykToken, organisations: List[SnykOrganisation], wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[(SnykOrganisation, String)]] = {
    Attempt.labelledTraverse(organisations) { organisation =>
      val snykProjectsUrl = s"https://snyk.io/api/v1/org/${organisation.id}/projects"
      val futureResponse = snykRequest(token, snykProjectsUrl, wsClient).get
        .map(response => response.body)
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

  def handleFuture[A](future: Future[A], label: String)(implicit ec: ExecutionContext): Attempt[A] = Attempt.fromFuture(future) {
    case NonFatal(e) =>
      val failure = Failure(e.getMessage, s"Could not read $label from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
  }

  def allSnykRuns(configraun: Configuration, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[SnykProjectIssues]] = {
    for {
      token <- getSnykToken(configraun)
      requiredOrganisation <- getSnykOrganisation(configraun)

      organisationResponse <- Snyk.getSnykOrganisations(token, wsClient)
      organisations <- SnykDisplay.parseOrganisations(organisationResponse.body, requiredOrganisation)

      projectResponses <- Snyk.getProjects(token, organisations, wsClient)
      organisationAndProjects <- SnykDisplay.parseProjectResponses(projectResponses)
      labelledProjects = SnykDisplay.labelOrganisations(organisationAndProjects)

      vulnerabilitiesResponse <- Snyk.getProjectVulnerabilities(labelledProjects, token, wsClient)
      vulnerabilitiesResponseBodies = vulnerabilitiesResponse.map(a => a.body)

      parsedVulnerabilitiesResponse <- SnykDisplay.parseProjectVulnerabilities(vulnerabilitiesResponseBodies)

      results = SnykDisplay.labelProjects(labelledProjects, parsedVulnerabilitiesResponse)
    } yield results
  }

  def getSnykToken(configraun: Configuration): Attempt[SnykToken] = configraun.getAsString("/snyk/token") match {
    case Left(error) =>
      val failure = Failure(error.message, "Could not read Snyk token from aws parameter store", 500, None, Some(error.e))
      Attempt.Left(failure)
    case Right(token) => Attempt.Right(SnykToken(token))
  }

  def getSnykOrganisation(configraun: Configuration): Attempt[SnykGroupId] = configraun.getAsString("/snyk/organisation") match {
    case Left(error) =>
      val failure = Failure(error.message, "Could not read Snyk organisation from aws parameter store", 500, None, Some(error.e))
      Attempt.Left(failure)
    case Right(groupId) => Attempt.Right(SnykGroupId(groupId))
  }

}
