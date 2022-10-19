package logic

import com.google.cloud.securitycenter.v1.Finding.Severity
import com.google.cloud.securitycenter.v1.SecurityCenterClient.ListFindingsPagedResponse
import com.google.cloud.securitycenter.v1.{Finding, ListFindingsRequest, OrganizationName, SecurityCenterClient, SourceName}
import com.google.protobuf.Value
import config.Config
import model.{GcpFinding,GcpReportSummary,GcpReportSummaryWithFindings}
import org.joda.time.DateTime
import play.api.{Configuration, Logging}
import utils.attempt.Attempt

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

object GcpDisplay extends Logging {
 
  val dateTimePattern = "dd/MM/yy"

  def reportStatusSummary(reportFindings: Map[String, Seq[GcpFinding]]): List[(String, GcpReportSummaryWithFindings)] = {
    sortProjectsByReportSummary(reportFindings.map { case (projectName, findings) =>
          (projectName, GcpReportSummaryWithFindings(
            GcpReportSummary(
              findings.count(_.severity == Severity.CRITICAL),
              findings.count(_.severity == Severity.HIGH),
              findings.count(_.severity == Severity.MEDIUM),
              findings.count(_.severity == Severity.LOW),
              findings.count(_.severity == Severity.SEVERITY_UNSPECIFIED)
            ),findings ))
        }.toList)
  }

  def sortProjectsByReportSummary(projectSummaryWithFindings: List[(String, GcpReportSummaryWithFindings)]): List[(String, GcpReportSummaryWithFindings)] = {
    projectSummaryWithFindings.sortBy { case (project, summaryWithFindings) => 
      val reportSummary = summaryWithFindings.reportSummary
      (-reportSummary.critical, -reportSummary.high, -reportSummary.medium, -reportSummary.low, -reportSummary.unspecified, project)
    }
  }
  
  def getGcpFindings(org: OrganizationName, client: SecurityCenterClient, config: Configuration): Attempt[List[GcpFinding]] = {
    Attempt.Right {
      val gcpPagedResponse: ListFindingsPagedResponse = callGcpApi(org, client, config)
      val allSccData = gcpPagedResponse.iterateAll().iterator().asScala.toList
      logger.info(s"Gathered all GCP response data, total findings: ${allSccData.length}")
      allSccData.map{  result =>
        val finding: Finding = result.getFinding
        GcpFinding(
          getProjectNameFromUri(finding.getExternalUri).getOrElse("unknown"),
          finding.getCategory,
          finding.getSeverity,
          new DateTime(finding.getEventTime.getSeconds * 1000),
          Option(finding.getSourcePropertiesOrDefault("Explanation", Value.newBuilder.build).getStringValue),
          Option(finding.getSourcePropertiesOrDefault("Recommendation", Value.newBuilder.build).getStringValue)
        )
      }
    }
  }

  def callGcpApi(org: OrganizationName, client: SecurityCenterClient, config: Configuration): SecurityCenterClient.ListFindingsPagedResponse = {
      val source = Config.gcpSccAuthentication(config).sourceId
      val sourceName: SourceName = SourceName.of(org.getOrganization, source)
      val filter = """state = "ACTIVE" AND -sourceProperties.ResourcePath : "projects/sys-""""
      val request: ListFindingsRequest.Builder = ListFindingsRequest.newBuilder
        .setParent(sourceName.toString)
        .setFilter(filter)
        .setPageSize(1000)
      client.listFindings(request.build)
    }

  val severities = List(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.UNRECOGNIZED)
  def sortFindings(findings: List[GcpFinding]): List[GcpFinding] = findings.sortBy{ finding =>
    val severity = severities.indexOf(finding.severity)
      val date = finding.eventTime.getMillis
    (severity, -date)
  }

    def getProjectNameFromUri(uri: String): Option[String] = {
      val regexPattern = new Regex("""(?<=project=).*""")
      regexPattern.findFirstIn(uri)
    }

  def preview(s: String, n: Int): String = {
    if (s.length <= n) s else s.take(s.lastIndexWhere(_.isSpaceChar, n + 1)).trim
  }
}
