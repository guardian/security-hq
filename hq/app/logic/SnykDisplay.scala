package logic

import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import model._
import model.Serializers._

import scala.util.{Success, Try}
import scala.util.control.NonFatal

object SnykDisplay {

  def parseOrganisations(rawJson: String, snykGroupId: SnykGroupId)(implicit ec: ExecutionContext): Attempt[List[SnykOrganisation]] = {
    for {
      organisationList <- parseJsonToOrganisationList(rawJson)
      guardianOrganisationList = organisationList filter {
        case SnykOrganisation(_, _, Some(group)) => group.id==snykGroupId.value
        case _ => false
      }
    } yield guardianOrganisationList
  }

  private def parseJsonToError(s: String): SnykError = Try(Json.parse(s).validateOpt[SnykError]) match {
    case Success(JsSuccess(Some(error), _)) => error
    case _ => SnykError(s)
  }

  def parseJsonToObject[A](label: String, rawJson: String, f: (String => JsResult[A])): Attempt[A] = Try(f(rawJson)) match {
    case Success(JsSuccess(parsedObject, _)) => Attempt.Right(parsedObject)
    case Success(JsError(e)) =>
      val error = parseJsonToError(rawJson)
      val failure = Failure(s"Unable to find $label from $rawJson: $e", s"Could not read Snyk response (${error.error})", 502, None, None)
      Attempt.Left(failure)
    case scala.util.Failure(e) =>
      val failure = Failure(s"Unable to find $label from $rawJson", s"Could not read Snyk response ($rawJson)", 502, None, Some(e))
      Attempt.Left(failure)
  }

  def parseJsonToOrganisationList(s: String): Attempt[List[SnykOrganisation]] =
    parseJsonToObject[List[SnykOrganisation]]("organisations", s, body => {(Json.parse(body) \ "orgs").validate[List[SnykOrganisation]]} )

  def getProjectIdList(organisationAndRequestList: List[(SnykOrganisation, String)])(implicit ec: ExecutionContext): Attempt[List[((SnykOrganisation, String), List[SnykProject])]] = {
    Attempt.labelledTraverse(organisationAndRequestList) { s =>
      parseJsonToProjectIdList(s._2)
    }
  }

  def parseJsonToProjectIdList(s: String): Attempt[List[SnykProject]] =
    parseJsonToObject("project ids", s, body => (Json.parse(body) \ "projects").validate[List[SnykProject]] )

  def parseProjectVulnerabilitiesBody(s: String): Attempt[SnykProjectIssues] =
  parseJsonToObject("project vulnerabilities", s, body => Json.parse(body).validate[SnykProjectIssues])

  def parseProjectVulnerabilities(bodies: List[String])(implicit ec: ExecutionContext): Attempt[List[SnykProjectIssues]] = {
    Attempt.traverse(bodies)(parseProjectVulnerabilitiesBody)
  }

  def linkToSnykProject(snykProjectIssues: SnykProjectIssues, queryString: Option[String]): String = {
    snykProjectIssues.project match {
      case Some(project: SnykProject) if project.organisation.nonEmpty =>
        s"https://snyk.io/org/${project.organisation.get.name}/project/${project.id}/${queryString.getOrElse("")}"
      case _ => ""
    }
  }

  def labelOrganisations(orgAndProjects: List[((SnykOrganisation, String), List[SnykProject])]): List[SnykProject] =
    orgAndProjects.flatMap{ case ((organisation, _), projects) => projects.map(project => project.copy(organisation = Some(organisation)))}

  def labelProjects(projects: List[SnykProject], responses: List[SnykProjectIssues]): List[SnykProjectIssues] = {
    projects.zip(responses).map(a => a._2.copy(project = Some(a._1)))
  }

  def sortProjects(projects: List[SnykProjectIssues]): List[SnykProjectIssues] =
    projects.sortBy(spi => (-spi.high, -spi.medium, -spi.low, spi.project.get.name))
}