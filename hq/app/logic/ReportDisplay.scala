package logic

import model._
import DateUtils.dayDiff
import org.joda.time.{DateTime, DateTimeZone}


object ReportDisplay {


  private[logic] def lastActivityDate(cred: IAMCredential): Option[DateTime] = {
    val allDates =
      cred.passwordLastUsed.toSeq ++ cred.accessKey1LastUsedDate.toSeq  ++ cred.accessKey2LastUsedDate.toSeq
    allDates.sortWith(_.isAfter(_)).collectFirst { case date if date.isBefore(DateTime.now(DateTimeZone.UTC)) => date }
  }

  private[logic] def key1Status(cred: IAMCredential): KeyStatus = {
    if (cred.accessKey1Active)
      AccessKeyEnabled
    else if (!cred.accessKey1Active && cred.accessKey1LastUsedDate.nonEmpty)
      AccessKeyDisabled
    else NoKey
  }

  private[logic] def key2Status(cred: IAMCredential): KeyStatus = {
    if (cred.accessKey2Active)
      AccessKeyEnabled
    else if (!cred.accessKey2Active && cred.accessKey2LastUsedDate.nonEmpty)
      AccessKeyDisabled
    else NoKey
  }

  private[logic] def machineReportStatus(cred: IAMCredential): ReportStatus = {
    if (!Seq(key1Status(cred), key2Status(cred)).contains(AccessKeyEnabled))
      Amber
    else Green
  }

  private[logic] def humanReportStatus(cred: IAMCredential): ReportStatus = {
    if (!cred.mfaActive)
      Red
    else if (Seq(key1Status(cred), key2Status(cred)).contains(AccessKeyEnabled))
      Amber
    else Green
  }

  def toCredentialReportDisplay(report: IAMCredentialsReport): CredentialReportDisplay = {
    report.entries.filterNot(_.rootUser).foldLeft(CredentialReportDisplay(report.generatedAt)) { (report, cred) =>
      val machineUser =
        if (!cred.passwordEnabled.getOrElse(false)) {
          Some(MachineUser(cred.user, key1Status(cred), key2Status(cred), machineReportStatus(cred), dayDiff(lastActivityDate(cred))))
        } else None

      val humanUser =
        if (cred.passwordEnabled.getOrElse(false)) {
          Some(HumanUser(cred.user, cred.mfaActive, key1Status(cred), key2Status(cred), humanReportStatus(cred), dayDiff(lastActivityDate(cred))))
        } else None

      report.copy(
        machineUsers = report.machineUsers ++ machineUser, humanUsers = report.humanUsers ++ humanUser)
    }
  }

  def checkNoKeyExists(keyStatuses: KeyStatus* ): Boolean = {
    keyStatuses.forall(_ == NoKey)
  }

  def toDayString(day: Option[Long]): String = day match {
    case Some(0) => "Today"
    case Some(1) => "Yesterday"
    case Some(d) => s"${d.toString} days ago"
    case _ => ""
  }
}
