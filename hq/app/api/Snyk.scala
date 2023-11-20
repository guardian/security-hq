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
    val snykOrgUrl = "https://snyk.io/api/orgs"
    val futureResponse = snykRequest(token, snykOrgUrl, wsClient).get()
    handleFuture(futureResponse, "organisation")
  }

  def getProjects(token: SnykToken, organisations: List[SnykOrganisation], wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[(SnykOrganisation, String)]] = {
    Attempt.labelledTraverse(organisations) { organisation =>
      val snykProjectsUrl = s"https://snyk.io/api/org/${organisation.id}/projects"
      val futureResponse = snykRequest(token, snykProjectsUrl, wsClient).get()
        .map(response => response.body)
      handleFuture(futureResponse, "project")
    }
  }

  def getOrganisationVulnerabilities(organisation: SnykOrganisation, token: SnykToken, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[String] = {
    val snykIssuesUrl = s"https://snyk.io/api/reporting/issues/latest?sortBy=priorityScore&order=desc&perPage=1000"
    val orgIssuesFilter = Json.obj(
      "filters" -> Json.obj(
        "orgs" -> JsArray(List(
          JsString(organisation.id))
        ),
        "types" -> JsArray(List(
          JsString("vuln")
        )),
        "ignored" -> JsBoolean(false),
        "patched" -> JsBoolean(false),
        "isFixed" -> JsBoolean(false)
      )
    )
    val futureResponse = snykRequest(token, snykIssuesUrl, wsClient).post(orgIssuesFilter)
      .map(response => response.body)
    handleFuture(futureResponse, "org vulnerabilities")
  }

  def handleFuture[A](future: Future[A], label: String)(implicit ec: ExecutionContext): Attempt[A] = Attempt.fromFuture(future) {
    case NonFatal(e) =>
      val failure = Failure(e.getMessage, s"Could not read $label from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
  }

  def allSnykRuns(snykConfig: SnykConfig, wsClient: WSClient)(implicit ec: ExecutionContext): Attempt[List[SnykOrganisationIssues]] = {
    for {
      organisationResponse <- Snyk.getSnykOrganisations(snykConfig.snykToken, wsClient)
      organisations <- SnykDisplay.parseOrganisations(organisationResponse.body, snykConfig.snykGroupId)

      filteredOrgs = organisations.filterNot { org =>
        snykConfig.excludeOrgs.contains(org.name)
      }

      vulnerabilitiesResponse <- Attempt.labelledTraverse(filteredOrgs)(
        Snyk.getOrganisationVulnerabilities(_, snykConfig.snykToken, wsClient)
      )
      orgIssues <- SnykDisplay.parseOrganisationVulnerabilities(vulnerabilitiesResponse)
    } yield orgIssues
  }

}
