package logic

import config.Config
import play.api.libs.json._
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import model._
import model.Serializers._
import play.api.Logging

import scala.util.{Success, Try}

object SnykDisplay extends Logging {

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

  def parseJsonToObject[A](label: String, rawJson: String, f: String => JsResult[A]): Attempt[A] = Try(f(rawJson)) match {
    case Success(JsSuccess(parsedObject, _)) => Attempt.Right(parsedObject)
    case Success(JsError(e)) =>
      val error = parseJsonToError(rawJson)
      val failure = Failure(s"Unable to find $label from $rawJson: $e", s"Could not read Snyk response (${error.error})", 502, None, None)
      logger.warn(s"failed to parse json response from snyk ${e}")
      Attempt.Left(failure)
    case scala.util.Failure(e) =>
      val failure = Failure(s"Unable to find $label from $rawJson", s"Could not read Snyk response ($rawJson)", 502, None, Some(e))
      logger.warn(s"failed to fetch from snyk: $rawJson")
      Attempt.Left(failure)
  }

  def parseProjectResponses(organisationAndRequestList: List[(SnykOrganisation, String)])(implicit ec: ExecutionContext): Attempt[List[((SnykOrganisation, String), List[SnykProject])]] =
    Attempt.labelledTraverse(organisationAndRequestList) { case (_, body) =>
      parseJsonToProjectIdList(body)
    }

  def parseJsonToOrganisationList(s: String): Attempt[List[SnykOrganisation]] =
    parseJsonToObject("organisations", s, body => (Json.parse(body) \ "orgs").validate[List[SnykOrganisation]])

  def parseJsonToProjectIdList(s: String): Attempt[List[SnykProject]] =
    parseJsonToObject("project ids", s, body => (Json.parse(body) \ "projects").validate[List[SnykProject]])

  def parseOrganisationVulnerabilities(organisationAndResponseList: List[(SnykOrganisation, String)])(implicit ec: ExecutionContext): Attempt[List[SnykOrganisationIssues]] =
    Attempt.traverse(organisationAndResponseList) { case (organisation, body) =>
      parseOrganisationVulnerabilitiesBody(body).map(projectIssues => SnykOrganisationIssues(organisation, projectIssues))
    }

  def parseOrganisationVulnerabilitiesBody(s: String): Attempt[List[SnykProjectIssues]] =
    parseJsonToObject("project vulnerabilities", s, body => {
      val orgIssuesResult = (Json.parse(body) \ "results").validate[List[SnykProjectIssue]]
      orgIssuesResult.map(groupProjectIssues)
    })

  def groupProjectIssues(projectsAndIssues: List[SnykProjectIssue]): List[SnykProjectIssues] =
    projectsAndIssues.groupBy(_.project).map { case (project, issues) =>
      SnykProjectIssues(project, issues)
    }.toList

  def linkToSnykIssue(issue: SnykProjectIssue): String =
    issue.project.fold("") { project =>
      s"${project.url}#issue-${issue.issue.id}"
    }

  def sortOrgs(orgs: List[SnykOrganisationIssues]): List[SnykOrganisationIssues] =
    orgs.sortBy(soi => (-soi.critical, -soi.high, -soi.medium, -soi.low, soi.organisation.name))

  def sortProjects(projects: List[SnykProjectIssues]): List[SnykProjectIssues] =
    projects.sortBy(spi => (-spi.critical, -spi.high, -spi.medium, -spi.low, spi.project.fold("")(_.name)))

  def sortIssuesByDate(projectIssues: List[SnykProjectIssue]): List[SnykProjectIssue] =
    projectIssues.sortBy(_.introducedDate)

  def longLivedHighVulnerabilities(organisationIssues: SnykOrganisationIssues): List[SnykProjectIssue] = {
    for {
      project <- organisationIssues.projectIssues
      vuln <- project.vulnerabilities
      if vuln.issue.severity.equalsIgnoreCase("high")
      if DateUtils.dayDiff(vuln.introducedDate) > Config.snykMaxAcceptableAgeOfHighVulnerabilities
    } yield vuln
  }

  def longLivedCriticalVulnerabilities(organisationIssues: SnykOrganisationIssues): List[SnykProjectIssue] = {
    for {
      project <- organisationIssues.projectIssues
      vuln <- project.vulnerabilities
      if vuln.issue.severity.equalsIgnoreCase("critical")
      if DateUtils.dayDiff(vuln.introducedDate) > Config.snykMaxAcceptableAgeOfCriticalVulnerabilities
    } yield vuln
  }

  def top5OldCriticalOrHighVulnerabilities(orgIssues: SnykOrganisationIssues): List[SnykProjectIssue] = {
    (sortIssuesByDate(longLivedCriticalVulnerabilities(orgIssues)) ++
      sortIssuesByDate(longLivedHighVulnerabilities(orgIssues))).take(5)
  }
}