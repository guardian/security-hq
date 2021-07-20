package schedule

import model.{AwsAccount, IAMAlertTargetGroup, IamAuditUser, VulnerableUser}
import org.joda.time.DateTime
import schedule.IamDeadline.getNearestDeadline

object IamUsersToDisable {
  def usersToDisable(flaggedUsers: Map[AwsAccount, Seq[IAMAlertTargetGroup]], dynamo: Dynamo): Map[AwsAccount, Seq[IamAuditUser]] = {
    flaggedUsers.map { case (awsAccount, targetGroups) =>
      awsAccount -> getUsersToDisable(targetGroups.flatMap(_.users), awsAccount, dynamo)
    }
  }

  def getUsersToDisable(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: Dynamo): Seq[IamAuditUser] = {
    users.flatMap{ user =>
      dynamo.getAlert(awsAccount, user.username).filter(u => toDisableToday(getNearestDeadline(u.alerts)))
    }
  }

  def toDisableToday(deadline: DateTime, today: DateTime = DateTime.now): Boolean =
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay
}
