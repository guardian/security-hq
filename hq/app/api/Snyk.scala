package api

import logic.SnykDisplay
import model._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import utils.attempt.{Attempt, FailedAttempt, Failure}
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
    ???
  }

  def handleFuture[A](future: Future[A], label: String)(implicit ec: ExecutionContext): Attempt[A] = Attempt.fromFuture(future) {
    case NonFatal(e) =>
      val failure = Failure(e.getMessage, s"Could not read $label from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
  }

  def allSnykRuns(snykConfig: SnykConfig, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[SnykProjectIssues]] = {
    for {
      organisationResponse <- Snyk.getSnykOrganisations(snykConfig.snykToken, wsClient)
      organisations <- SnykDisplay.parseOrganisations(organisationResponse.body, snykConfig.snykGroupId)

      projectResponses <- Snyk.getProjects(snykConfig.snykToken, organisations, wsClient)
      organisationAndProjects <- SnykDisplay.parseProjectResponses(projectResponses)
      labelledProjects = SnykDisplay.labelOrganisations(organisationAndProjects)

      vulnerabilitiesResponse <- Snyk.getProjectVulnerabilities(labelledProjects, snykConfig.snykToken, wsClient)
      vulnerabilitiesResponseBodies = vulnerabilitiesResponse.map(a => a.body)

      parsedVulnerabilitiesResponse <- SnykDisplay.parseProjectVulnerabilities(vulnerabilitiesResponseBodies)

      results = SnykDisplay.labelProjects(labelledProjects, parsedVulnerabilitiesResponse)
    } yield results
  }

}
