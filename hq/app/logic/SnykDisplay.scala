package logic

import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.libs.ws.{WSClient, WSRequest}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object SnykDisplay {

  implicit val snykOrgFormat: Format[SnykOrg] = (
    (JsPath \ "name").format[String]
      and
      (JsPath \ "id").format[String]
    )(SnykOrg.apply, unlift(SnykOrg.unapply))

  implicit val snykProjectFormat: Format[SnykProject] = (
    (JsPath \ "name").format[String]
      and
      (JsPath \ "id").format[String]
    )(SnykProject.apply, unlift(SnykProject.unapply))

  implicit val snykIssueReads: Reads[SnykIssue] = (
    (JsPath \ "title").read[String]
      and
      (JsPath \ "id").read[String]
      and
      (JsPath \ "severity").read[String]
    )(SnykIssue.apply _)

  implicit val snykProjectIssuesReads: Reads[SnykProjectIssues] = (
    Reads.pure("Unknown")
      and
      Reads.pure("Unknown")
      and
      (JsPath \ "ok").read[Boolean]
      and
      (JsPath \ "issues" \ "vulnerabilities").read[List[SnykIssue]]
    )(SnykProjectIssues.apply _)

  def getSnykOrganisations(token: Token, wsClient: WSClient)(implicit ec:ExecutionContext) = {

    System.out.println(token.value)
    val snykOrgUrl = "https://snyk.io/api/v1/orgs"

    val futureResponse = wsClient.url(snykOrgUrl)
      .addHttpHeaders("Authorization" -> s"token ${token.value}")
      .get

    Attempt.fromFuture(futureResponse) { case NonFatal(e) => {
      val failure = Failure(e.getMessage, "Could not read organisations from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
    }}
  }

  def getOrganisationId(s: String, organisation: Organisation) = {
    val id = for {
      orglist <- (Json.parse(s) \ "orgs").asOpt[List[SnykOrg]]
      org <- orglist.find(_.name == organisation.value)
    } yield org.id
    val f = Failure(s"Unable to find organisation from ${s}", "Could not read Snyk response", 502, None, None)
    Attempt.fromOption(id, FailedAttempt(f))
  }

  def getProjects(token: Token, id: String, wsClient: WSClient)(implicit ec:ExecutionContext) = {
        val snykProjectsUrl = s"https://snyk.io/api/v1/org/${id}/projects"
        val a = wsClient.url(snykProjectsUrl)
          .addHttpHeaders("Authorization" -> s"token ${token.value}")
          .get()

    Attempt.fromFuture(a) { case NonFatal(e) => {
      val failure = Failure(e.getMessage, "Could not read projects from Snyk", 502, None, Some(e))
      FailedAttempt(failure)
    }}
  }

  def getProjectIdList(s: String) = {
    val projectIds = (Json.parse(s) \ "projects").asOpt[List[SnykProject]]
    val f = Failure(s"Unable to find project ids from ${s}", "Could not read Snyk response", 502, None, None)
    Attempt.fromOption(projectIds, FailedAttempt(f))
  }

  def getProjectVulnerabilities(id: String, projects: List[SnykProject], token: Token, wsClient: WSClient)(implicit ec:ExecutionContext) = {
    val projectVulnerabilityResponses = projects
      .map(project => {
        val snykProjectUrl = s"https://snyk.io/api/v1/org/${id}/project/${project.id}/issues"

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
    Attempt.traverse(projectVulnerabilityResponses)(
      projectVulnerabilityResponse => Attempt.fromFuture(projectVulnerabilityResponse) {
        case NonFatal(e) => {
          val failure = Failure(e.getMessage, "Could not read project vulnerabilities from Snyk", 502, None, Some(e))
          FailedAttempt(failure)
        }
      }
    )
  }

  def parseProjectVulnerabilities(projects: List[String])(implicit ec:ExecutionContext) = {
    val b = projects.map( s => {
      val projectVulnerabilities = (Json.parse(s)).asOpt[SnykProjectIssues]
      val f = Failure(s"Unable to find project vulnerabilities from ${s}", "Could not read Snyk response", 502, None, None)
      Attempt.fromOption(projectVulnerabilities, FailedAttempt(f))
    })
    Attempt.traverse(b)(a => a)
  }

  def labelProjects(projects: List[SnykProject], responses: List[SnykProjectIssues]) = {
    projects.zip(responses).map(a => a._2.withName(a._1.name).withId(a._1.id))
  }
}

case class SnykOrg(name: String, id: String)

case class SnykProject(name: String, id: String)

case class SnykIssue(title: String, id: String, severity: String)

case class SnykProjectIssues(name: String, id: String, ok: Boolean, vulnerabilities: List[SnykIssue])  {
  def withName(name: String) = new SnykProjectIssues(name, this.id, this.ok, this.vulnerabilities)
  def withId(id: String) = new SnykProjectIssues(this.name, id, this.ok, this.vulnerabilities)
  def high = vulnerabilities.filter(s => s.severity.equalsIgnoreCase("high")).length
  def medium = vulnerabilities.filter(s => s.severity.equalsIgnoreCase("medium")).length
  def low = vulnerabilities.filter(s => s.severity.equalsIgnoreCase("low")).length
}

class Token(val value: String) extends AnyVal
class Organisation(val value: String) extends AnyVal