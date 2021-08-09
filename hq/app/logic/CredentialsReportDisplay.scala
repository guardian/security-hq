package logic

import logic.DateUtils.dayDiff
import model._
import org.joda.time.{DateTime, DateTimeZone, Days}
import utils.attempt.FailedAttempt
import java.net.URLEncoder


object CredentialsReportDisplay {

  case class ReportSummary(warnings: Int, errors: Int, other: Int)

  private[logic] def lastActivityDate(cred: IAMCredential): Option[DateTime] = {
    val allDates =
      cred.passwordLastUsed.toSeq ++ cred.accessKey1LastUsedDate.toSeq ++ cred.accessKey2LastUsedDate.toSeq
    allDates.sortWith(_.isAfter(_)).collectFirst { case date if date.isBefore(DateTime.now(DateTimeZone.UTC)) => date }
  }

  private[logic] def accessKey1Details(cred: IAMCredential): AccessKey = {
    if (cred.accessKey1Active)
      AccessKey(AccessKeyEnabled, cred.accessKey1LastRotated)
    else if (!cred.accessKey1Active && cred.accessKey1LastUsedDate.nonEmpty)
      AccessKey(AccessKeyDisabled, cred.accessKey1LastRotated)
    else AccessKey(NoKey, None)
  }

  private[logic] def accessKey2Details(cred: IAMCredential): AccessKey = {
    if (cred.accessKey2Active)
      AccessKey(AccessKeyEnabled, cred.accessKey2LastRotated)
    else if (!cred.accessKey2Active && cred.accessKey2LastUsedDate.nonEmpty)
      AccessKey(AccessKeyDisabled, cred.accessKey2LastRotated)
    else AccessKey(NoKey, None)
  }

  private[logic] def machineReportStatus(cred: IAMCredential): ReportStatus = {
    if (!Seq(accessKey1Details(cred).keyStatus, accessKey2Details(cred).keyStatus).contains(AccessKeyEnabled))
      Amber
    else if (Days.daysBetween(lastActivityDate(cred).getOrElse(DateTime.now), DateTime.now).getDays > 365)
      Blue
    else Green
  }

  private[logic] def humanReportStatus(cred: IAMCredential): ReportStatus = {
    if (!cred.mfaActive)
      Red
    else if (Seq(accessKey1Details(cred).keyStatus, accessKey2Details(cred).keyStatus).contains(AccessKeyEnabled))
      Amber
    else if (Days.daysBetween(lastActivityDate(cred).getOrElse(DateTime.now), DateTime.now).getDays > 365)
      Blue
    else Green
  }

  def linkForAwsConsole(stack: AwsStack): String = {
    s"https://${stack.region}.console.aws.amazon.com/cloudformation/home?${stack.region}#/stack/detail?stackId=${URLEncoder.encode(stack.id, "utf-8")}"
  }

  def toCredentialReportDisplay(report: IAMCredentialsReport): CredentialReportDisplay = {

    report.entries.filterNot(_.rootUser).foldLeft(CredentialReportDisplay(report.generatedAt)) { (report, cred) =>
      val machineUser =
        if (!cred.passwordEnabled.getOrElse(false)) {
          Some(MachineUser(
            cred.user,
            accessKey1Details(cred),
            accessKey2Details(cred),
            machineReportStatus(cred),
            dayDiff(lastActivityDate(cred)),
            stack = cred.stack,
            tags = cred.tags
          ))
        } else None

      val humanUser =
        if (cred.passwordEnabled.getOrElse(false)) {
          Some(HumanUser(
            cred.user,
            cred.mfaActive,
            accessKey1Details(cred),
            accessKey2Details(cred),
            humanReportStatus(cred),
            dayDiff(lastActivityDate(cred)),
            stack = cred.stack,
            tags = cred.tags
          ))
        } else None

      report.copy(
        machineUsers = report.machineUsers ++ machineUser,
        humanUsers = report.humanUsers ++ humanUser
      )
    }
  }

  def checkNoKeyExists(keyStatuses: AccessKey*): Boolean = {
    keyStatuses.forall(_.keyStatus == NoKey)
  }

  def toDayString(day: Option[Long]): String = day match {
    case Some(0) => "Today"
    case Some(1) => "Yesterday"
    case Some(d) => s"${d.toString} days ago"
    case _ => ""
  }

  def reportStatusSummary(report: CredentialReportDisplay): ReportSummary = {
    val reportStatusSummary = (report.humanUsers ++ report.machineUsers).map(_.reportStatus)

    val warnings = reportStatusSummary.count(_.isInstanceOf[Amber.type])
    val errors = reportStatusSummary.count(_.isInstanceOf[Red.type])
    val others = reportStatusSummary.count(_.isInstanceOf[Blue.type])

    ReportSummary(warnings, errors, others)
  }

  def exposedKeysSummary(allReports: Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]]): Map[AwsAccount, Boolean] = {
    allReports.mapValues {
      case Right(keys) if keys.nonEmpty => true
      case _ => false
    }
  }

  def sortAccountsByReportSummary[L](reports: List[(AwsAccount, Either[L, CredentialReportDisplay])]): List[(AwsAccount, Either[L, CredentialReportDisplay])] = {
    reports.sortBy {
      case (account, Right(report)) if reportStatusSummary(report).errors + reportStatusSummary(report).warnings != 0 =>
        (0, reportStatusSummary(report).errors * -1, reportStatusSummary(report).warnings * -1, account.name)
      case (account, Left(_)) =>
        (0, 1, 0, account.name)
      case (account, Right(_)) =>
        (1, 0, 0, account.name)
    }
  }

  def sortUsersByReportSummary(report: CredentialReportDisplay): CredentialReportDisplay = {
    report.copy(
      machineUsers = report.machineUsers.sortBy(user => (user.reportStatus, user.username)),
      humanUsers = report.humanUsers.sortBy(user => (user.reportStatus, user.username))
    )
  }

  implicit val reportStatusOrdering: Ordering[ReportStatus] = new Ordering[ReportStatus] {
    private def statusCode(status: ReportStatus): Int = status match {
      case Red => 0
      case Amber => 1
      case _ => 99
    }

    override def compare(x: ReportStatus, y: ReportStatus): Int = {
      statusCode(x) - statusCode(y)
    }
  }
}
