package logic

import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import model._
import model.Serializers._

object SnykDisplay {

  def getOrganisation(s: String, requiredOrganisation: SnykOrganisationName): Attempt[SnykOrganisation] = {
    val organisation = for {
      orglist <- parseJsonToOrganisationList(s)
      org <- orglist.find(_.name == requiredOrganisation.value)
    } yield org
    val error = parseJsonToError(s)
    val f = Failure(s"Unable to find organisation from $s", s"Could not read Snyk response (${error.error})", 502, None, None)
    Attempt.fromOption(organisation, FailedAttempt(f))
  }

  private def parseJsonToError(s: String): SnykError = try {
    Json.parse(s).asOpt[SnykError].getOrElse(SnykError(s))
  } catch {
    case _: JsonParseException => SnykError(s)
  }

  private def parseJsonToOrganisationList(s: String): Option[List[SnykOrganisation]] = try {
    (Json.parse(s) \ "orgs").asOpt[List[SnykOrganisation]]
  } catch {
    case _: JsonParseException => None
  }

  def getProjectIdList(s: String): Attempt[List[SnykProject]] = {
    val projects = parseJsonToProjectIdList(s)
    val error = parseJsonToError(s)
    val f = Failure(s"Unable to find project ids from $s", s"Could not read Snyk response (${error.error})", 502, None, None)
    Attempt.fromOption(projects, FailedAttempt(f))
  }

  private def parseJsonToProjectIdList(s: String) = try {
    (Json.parse(s) \ "projects").asOpt[List[SnykProject]]
  } catch {
    case _: JsonParseException => None
  }

  def parseProjectVulnerabilities(projects: List[String])(implicit ec:ExecutionContext): Attempt[List[SnykProjectIssues]] = {
    val projectVulnerabilitiesList = projects.map( s => {
      val projectVulnerabilities = parseJsonToProjectVulnerabilities(s)
      val error = parseJsonToError(s)
      val f = Failure(s"Unable to find project vulnerabilities from $s", s"Could not read Snyk response (${error.error})", 502, None, None)
      Attempt.fromOption(projectVulnerabilities, FailedAttempt(f))
    })
    Attempt.traverse(projectVulnerabilitiesList)(a => a)
  }

  private def parseJsonToProjectVulnerabilities(s: String) = try {
    Json.parse(s).asOpt[SnykProjectIssues]
  } catch {
    case _: JsonParseException => None
  }

  def labelOrganisations(projects: List[SnykProject], snykOrg: SnykOrganisation): List[SnykProject] = {
    projects.map(project => project.copy(organisation = Some(snykOrg)))
  }

  def labelProjects(projects: List[SnykProject], responses: List[SnykProjectIssues]): List[SnykProjectIssues] = {
    projects.zip(responses).map(a => a._2.copy(project = Some(a._1)))
  }

  def sortProjects(projects: List[SnykProjectIssues]): List[SnykProjectIssues] =
    projects.sortBy(spi => (-spi.high, -spi.medium, -spi.low, spi.project.get.name))
}