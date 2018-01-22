package logic

import model.{Amber, CredentialReportDisplay, Red}


object CredentialsDisplay {

  case class CredentialsIcons(warnings: Int, errors: Int, other: Int)

  def credentialsIcons(report: CredentialReportDisplay): CredentialsIcons = {

    val reportStatus = ReportDisplay.reportStatusSummary(report)

    val (warnings, errors, other) = reportStatus.foldLeft(0,0,0) {
      case ( (war, err, oth), Amber ) => (war+1, err, oth)
      case ( (war, err, oth), Red ) => (war, err+1, oth)
      case ( (war, err, oth), _ ) => (war, err, oth+1)
    }

    CredentialsIcons(warnings, errors, other)
  }

}
