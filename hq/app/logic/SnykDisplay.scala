package logic

import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import model._
import model.Serializers._

object SnykDisplay {

  def parseOrganisations(s: String, snykGroupId: SnykGroupId)(implicit ec: ExecutionContext): Attempt[List[SnykOrganisation]] = {
    for {
      organisationList <- parseJsonToOrganisationList(s)
      guardianOrganisationList = organisationList filter {
        case SnykOrganisation(_, _, Some(group)) => group.id==snykGroupId.value
        case _ => false
      }
    } yield guardianOrganisationList

  }

  private def parseJsonToError(s: String): SnykError = try {
    Json.parse(s).asOpt[SnykError].getOrElse(SnykError(s))
  } catch {
    case e: JsonParseException => SnykError(e.getMessage)
  }

  def parseJsonToOrganisationList(s: String): Attempt[List[SnykOrganisation]] = try {
      Attempt.Right((Json.parse(s) \ "orgs").as[List[SnykOrganisation]])
    } catch {
    case e: JsonParseException =>
      val f = Failure(s"Unable to find organisation from $s", s"Could not read Snyk response ($s)", 502, None, Some(e))
      Attempt.Left(f)
    case e: Exception =>
      val error = parseJsonToError(s)
      val f = Failure(s"Unable to find organisation from $s", s"Could not read Snyk response (${error.error})", 502, None, Some(e))
      Attempt.Left(f)
    }

  def getProjectIdList(ss: List[(SnykOrganisation, String)])(implicit ec: ExecutionContext): Attempt[List[(SnykOrganisation, List[SnykProject])]] = {
    Attempt.traverse(ss) { s =>
      val x = parseJsonToProjectIdList(s._2)
      x.map2(Attempt.Right(s._1))((a,b) => (b,a))
    }
  }

  def parseJsonToProjectIdList(body: String): Attempt[List[SnykProject]] = try {
    Attempt.Right((Json.parse(body) \ "projects").as[List[SnykProject]])
  } catch {
    case e: JsonParseException =>
      val f = Failure(s"Unable to find project vulnerabilities from $body", s"Could not read Snyk response ($body)", 502, None, Some(e))
      Attempt.Left(f)
    case e: Exception =>
      val error = parseJsonToError(body)
      val f = Failure(s"Unable to find project vulnerabilities from $body", s"Could not read Snyk response (${error.error})", 502, None, Some(e))
      Attempt.Left(f)
  }

  def parseProjectVulnerabilitiesBody(body: String): Attempt[SnykProjectIssues] = try {
      Attempt.Right(Json.parse(body).as[SnykProjectIssues])
    } catch {
      case e: JsonParseException =>
        val f = Failure(s"Unable to find project vulnerabilities from $body", s"Could not read Snyk response ($body)", 502, None, Some(e))
        Attempt.Left(f)
      case e: Exception =>
        val error = parseJsonToError(body)
        val f = Failure(
          s"Unable to find project vulnerabilities from $body",
          s"Could not read Snyk response (${error.error})",
          502, None, Some(e))
        Attempt.Left(f)
    }

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

  def labelOrganisations(orgAndProjects: List[(SnykOrganisation, List[SnykProject])]): List[SnykProject] =
    orgAndProjects.flatMap{ case (organisation, projects) => projects.map(project => project.copy(organisation = Some(organisation)))}

  def labelProjects(projects: List[SnykProject], responses: List[SnykProjectIssues]): List[SnykProjectIssues] = {
    projects.zip(responses).map(a => a._2.copy(project = Some(a._1)))
  }

  def sortProjects(projects: List[SnykProjectIssues]): List[SnykProjectIssues] =
    projects.sortBy(spi => (-spi.high, -spi.medium, -spi.low, spi.project.get.name))
}