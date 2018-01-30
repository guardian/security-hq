package logic

import play.api.libs.json._
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext

import model._
import model.Serializers._

object SnykDisplay {

  def getOrganisation(s: String, requiredOrganisation: SnykOrganisationName): Attempt[SnykOrganisation] = {
    val organisation = for {
      orglist <- (Json.parse(s) \ "orgs").asOpt[List[SnykOrganisation]]
      org <- orglist.find(_.name == requiredOrganisation.value)
    } yield org
    val error = Json.parse(s).asOpt[SnykError].getOrElse(SnykError(s))
    val f = Failure(s"Unable to find organisation from $s", s"Could not read Snyk response (${error.error})", 502, None, None)
    Attempt.fromOption(organisation, FailedAttempt(f))
  }

  def getProjectIdList(s: String): Attempt[List[SnykProject]] = {
    val projects = (Json.parse(s) \ "projects").asOpt[List[SnykProject]]
    val error = Json.parse(s).asOpt[SnykError].getOrElse(SnykError(s))
    val f = Failure(s"Unable to find project ids from $s", s"Could not read Snyk response (${error.error})", 502, None, None)
    Attempt.fromOption(projects, FailedAttempt(f))
  }

  def parseProjectVulnerabilities(projects: List[String])(implicit ec:ExecutionContext): Attempt[List[SnykProjectIssues]] = {
    val projectVulnerabilitiesList = projects.map( s => {
      val projectVulnerabilities = Json.parse(s).asOpt[SnykProjectIssues]
      val error = Json.parse(s).asOpt[SnykError].getOrElse(SnykError(s))
      val f = Failure(s"Unable to find project vulnerabilities from $s", s"Could not read Snyk response (${error.error})", 502, None, None)
      Attempt.fromOption(projectVulnerabilities, FailedAttempt(f))
    })
    Attempt.traverse(projectVulnerabilitiesList)(a => a)
  }

  def labelOrganisations(projects: List[SnykProject], snykOrg: SnykOrganisation): List[SnykProject] = {
    projects.map(project => project.copy(organisation = Some(snykOrg)))
  }

  def labelProjects(projects: List[SnykProject], responses: List[SnykProjectIssues]): List[SnykProjectIssues] = {
    projects.zip(responses).map(a => a._2.copy(project = Some(a._1)))
  }

  def sortProjects(projects: List[SnykProjectIssues]): List[SnykProjectIssues] =
    projects.sortBy(spi => (-spi.high, -spi.medium, -spi.low, spi.project.get.name))  //Negations to produce maximum-first sort.
}