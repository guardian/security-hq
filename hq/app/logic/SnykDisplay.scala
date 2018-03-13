package logic

import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import model._
import model.Serializers._

object SnykDisplay {

  def parseOrganisations(s: String, snykGroupId: SnykGroupId)(implicit ec: ExecutionContext): Attempt[List[SnykOrganisation]] = {
    println(snykGroupId)
    for {
      organisationList <- parseJsonToOrganisationList(s)
      guardianOrganisationList = organisationList filter {
        case SnykOrganisation(_, id, Some(group)) => group.id==snykGroupId.value && !id.equals("89877232-f84c-43af-9436-e9e0a61f640d")
        case _ => false
      }
    } yield guardianOrganisationList

  }

  private def parseJsonToError(s: String): SnykError = try {
    Json.parse(s).asOpt[SnykError].getOrElse(SnykError(s))
  } catch {
    case e: JsonParseException => SnykError(e.getMessage)
  }

  def parseJsonToObject[A](label: String, s: String, f: (String => A)): Attempt[A] = try {
    Attempt.Right(f(s))
  } catch {
    case e: JsonParseException =>
      val f = Failure(s"Unable to find $label from $s", s"Could not read Snyk response ($s)", 502, None, Some(e))
      Attempt.Left(f)
    case e: Exception =>
      val error = parseJsonToError(s)
      val f = Failure(s"Unable to find $label from $s", s"Could not read Snyk response (${error.error})", 502, None, Some(e))
      Attempt.Left(f)
  }

  def parseJsonToOrganisationList(s: String): Attempt[List[SnykOrganisation]] =
    parseJsonToObject("organisations", s, body => {(Json.parse(body) \ "orgs").as[List[SnykOrganisation]]} )

  def getProjectIdList(ss: List[(SnykOrganisation, String)])(implicit ec: ExecutionContext): Attempt[List[((SnykOrganisation, String), List[SnykProject])]] = {
    Attempt.labelledTraverse(ss) { s =>
      parseJsonToProjectIdList(s._2)
    }
  }

  def parseJsonToProjectIdList(s: String): Attempt[List[SnykProject]] =
    parseJsonToObject("project ids", s, body => (Json.parse(body) \ "projects").as[List[SnykProject]] )

  def parseProjectVulnerabilitiesBody(s: String): Attempt[SnykProjectIssues] =
  parseJsonToObject("project vulnerabilities", s, body => Json.parse(body).as[SnykProjectIssues])

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