package logic

import play.api.libs.json._
import play.api.libs.json.Reads._
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext

import model._

object SnykDisplay extends Serializers {

  def getOrganisationId(s: String, organisation: Organisation) = {
    val id = for {
      orglist <- (Json.parse(s) \ "orgs").asOpt[List[SnykOrg]]
      org <- orglist.find(_.name == organisation.value)
    } yield org.id
    val error = Json.parse(s).asOpt[SnykError].getOrElse(new SnykError(s))
    val f = Failure(s"Unable to find organisation from ${s}", s"Could not read Snyk response (${error.error})", 502, None, None)
    Attempt.fromOption(id, FailedAttempt(f))
  }

  def getProjectIdList(s: String) = {
    val projectIds = (Json.parse(s) \ "projects").asOpt[List[SnykProject]]
    val error = Json.parse(s).asOpt[SnykError].getOrElse(new SnykError(s))
    val f = Failure(s"Unable to find project ids from ${s}", s"Could not read Snyk response (${error.error})", 502, None, None)
    Attempt.fromOption(projectIds, FailedAttempt(f))
  }

  def parseProjectVulnerabilities(projects: List[String])(implicit ec:ExecutionContext) = {
    val b = projects.map( s => {
      val projectVulnerabilities = Json.parse(s).asOpt[SnykProjectIssues]
      val error = Json.parse(s).asOpt[SnykError].getOrElse(new SnykError(s))
      val f = Failure(s"Unable to find project vulnerabilities from ${s}", s"Could not read Snyk response (${error.error})", 502, None, None)
      Attempt.fromOption(projectVulnerabilities, FailedAttempt(f))
    })
    Attempt.traverse(b)(a => a)
  }

  def labelProjects(projects: List[SnykProject], responses: List[SnykProjectIssues]) = {
    projects.zip(responses).map(a => a._2.withName(a._1.name).withId(a._1.id))
  }
}