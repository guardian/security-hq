package schedule.vulnerable

import config.Config.iamAlertCadence
import model.{AwsAccount, IamAuditAlert, VulnerableUser}
import org.joda.time.{DateTime, Days}
import schedule.DynamoAlertService

/**
  * Each permanent credential which has been flagged as being vulnerable (either it needs rotating or requires multi-factor authentication),
  * is given a deadline. On the deadline date, the permanent credential will automatically be disabled by Security HQ unless the vulnerability
  * is addressed (either by rotating it or adding mfa).
  */
object IamDeadline {

  def sortUsersIntoWarningOrFinalAlerts(users: Seq[VulnerableUser]): (Seq[VulnerableUser], Seq[VulnerableUser]) = {
    val warningAlerts = users.filter(user => isWarningAlert(createDeadlineIfMissing(user.disableDeadline)))
    val finalAlerts = users.filter(user => isFinalAlert(createDeadlineIfMissing(user.disableDeadline)))
    (warningAlerts, finalAlerts)
  }

  def createDeadlineIfMissing(date: Option[DateTime]): DateTime = date.getOrElse(DateTime.now.plusDays(iamAlertCadence))

  def isWarningAlert(deadline: DateTime, today: DateTime = DateTime.now): Boolean = {
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusWeeks(1) ||
      deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusWeeks(3)
  }
  def isFinalAlert(deadline: DateTime, today: DateTime = DateTime.now): Boolean = deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusDays(1)

  // if the user is not present in dynamo, that means they've never been alerted before, so mark them as ready to be alerted
  def filterUsersToAlert(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: DynamoAlertService): Seq[VulnerableUser] = {
    val usersWithDeadline = enrichUsersWithDeadline(users, awsAccount, dynamo)
    usersWithDeadline.filter { user =>
      user.disableDeadline.exists(deadline => isWarningAlert(deadline) || isFinalAlert(deadline)) || user.disableDeadline.isEmpty
    }
  }

  // adds deadline to users when this field is present in dynamoDB
  private def enrichUsersWithDeadline(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: DynamoAlertService): Seq[VulnerableUser] = {
    users.map { user =>
      dynamo.getAlert(awsAccount, user.username).map { u =>
        user.copy(
          username = user.username,
          key1 = user.key1,
          key2 = user.key2,
          tags = user.tags,
          disableDeadline = Some(getNearestDeadline(u.alerts)))
      }.getOrElse(user)
    }
  }

  def getNearestDeadline(alerts: List[IamAuditAlert], today: DateTime = DateTime.now): DateTime = {
    val (nearestDeadline, _) = alerts.foldRight[(DateTime, Int)]((DateTime.now, iamAlertCadence)) {
      case (alert, (acc, startingNumberOfDays)) =>
        val daysBetweenTodayAndDeadline: Int = Days.daysBetween(today, alert.disableDeadline).getDays
        if (daysBetweenTodayAndDeadline < startingNumberOfDays) (alert.disableDeadline, daysBetweenTodayAndDeadline)
        else (acc, startingNumberOfDays)
    }
    nearestDeadline
  }

}
