package schedule

import model.{AwsAccount, IAMAlertTargetGroup, IamAuditUser, VulnerableUser}
import org.joda.time.DateTime
import schedule.IamDeadline.getNearestDeadline

object IamUsersToDisable {
  // call getUsersToDisable
  def usersToDisable(flaggedUsers: Map[AwsAccount, Seq[IAMAlertTargetGroup]]): Seq[IamAuditUser] = ???

  def getUsersToDisable(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: Dynamo): Seq[IamAuditUser] = {
    users.flatMap{ user =>
      dynamo.getAlert(awsAccount, user.username).filter(u => toDisableToday(getNearestDeadline(u.alerts)))
    }
  }

  def toDisableToday(deadline: DateTime, today: DateTime = DateTime.now): Boolean =
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay
}
