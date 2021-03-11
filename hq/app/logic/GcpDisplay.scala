package logic

import com.google.cloud.securitycenter.v1.{ListFindingsRequest, OrganizationName, SecurityCenterClient, SourceName}
import com.google.protobuf.Value
import config.Config
import model.GcpFinding
import org.joda.time.DateTime
import play.api.Configuration
import utils.attempt.Attempt

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.matching.Regex

object GcpDisplay {
  val dateTimePattern = "dd/MM/yy"

  def getGcpFindings(org: OrganizationName, client: SecurityCenterClient, config: Configuration): Attempt[List[GcpFinding]] = {
    Attempt.Right {
      callGcpApi(org, client, config).iterateAll.iterator.asScala.toList.map{  result =>
        val finding = result.getFinding
        GcpFinding(
          getProjectNameFromUri(finding.getExternalUri).getOrElse("unknown"),
          finding.getCategory,
          Option(finding.getSourcePropertiesOrDefault("SeverityLevel", Value.newBuilder.build).getStringValue),
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
      val filter = """-sourceProperties.ResourcePath : "projects/sys-"""" //TODO remove org level findings
      val request: ListFindingsRequest.Builder = ListFindingsRequest.newBuilder.setParent(sourceName.toString).setFilter(filter)
      client.listFindings(request.build)
    }

  val severities = List("High", "Medium", "Low")
  def sortFindings(findings: List[GcpFinding]): List[GcpFinding] = findings.sortBy{ finding =>
    val severity = severities.indexOf(finding.severity.getOrElse("Low"))
      val date = finding.eventTime.getMillis
    (severity, -date)
  }

    def getProjectNameFromUri(uri: String): Option[String] = {
      val regexPattern = new Regex("""(?<=project=).*""")
      regexPattern.findFirstIn(uri)
    }
}
