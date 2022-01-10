package logic

import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import GcpDisplay._
import com.google.cloud.securitycenter.v1.Finding.Severity


class GcPDisplayTest extends FreeSpec with Matchers {
    val dateTime = DateTime.now()

    "sortProjectsByReportSummary" - {
        "correctly sort two projects with different findings by severity" in {
            val gcpFindingCritical1 = GcpFinding ("project1","",Severity.CRITICAL,dateTime,None,None)
            val gcpFindingCritical2 = GcpFinding ("project2","",Severity.CRITICAL,dateTime,None,None)
            val gcpFindingMedium2:GcpFinding = GcpFinding ("project2","",Severity.MEDIUM,dateTime,None,None)
            val reportSummaryWithFindings1 = ReportSummaryWithFindings(ReportSummary(1,0,0,0,0),Seq(gcpFindingCritical1))
            val reportSummaryWithFindings2 = ReportSummaryWithFindings(ReportSummary(1,0,1,0,0),Seq(gcpFindingCritical2,gcpFindingMedium2)) 

            val unsortedFindings = Map("project1"->Seq(gcpFindingCritical1), "project2"->Seq(gcpFindingCritical2,gcpFindingMedium2))
            val sortedReport = List(("project2",reportSummaryWithFindings2),("project1",reportSummaryWithFindings1))

            sortProjectsByReportSummary(reportStatusSummary(unsortedFindings)) shouldEqual sortedReport
        }
    
        "when two projects have the same number of findings and severity order by project name" in {
            val gcpFindingCritical1 = GcpFinding ("projectB","",Severity.CRITICAL,dateTime,None,None)
            val gcpFindingCritical2 = GcpFinding ("projectA","",Severity.CRITICAL,dateTime,None,None)
            val reportSummaryWithFindings1 = ReportSummaryWithFindings(ReportSummary(1,0,0,0,0),Seq(gcpFindingCritical1))
            val reportSummaryWithFindings2 = ReportSummaryWithFindings(ReportSummary(1,0,0,0,0),Seq(gcpFindingCritical2)) 

            val unsortedFindings = Map("projectB"->Seq(gcpFindingCritical1), "projectA"->Seq(gcpFindingCritical2))
            val sortedReport = List(("projectA",reportSummaryWithFindings2),("projectB",reportSummaryWithFindings1))

            sortProjectsByReportSummary(reportStatusSummary(unsortedFindings)) shouldEqual sortedReport
        }
    }
}