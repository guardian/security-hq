package schedule

import model.{AwsAccount, IAMAlertTargetGroup, VulnerableUser}
import org.joda.time.DateTime
import schedule.vulnerable.IamDeadline.getNearestDeadline

object IamUsersToDisable {
  def usersToDisable(flaggedUsers: Map[AwsAccount, Seq[IAMAlertTargetGroup]], dynamo: Dynamo): Map[AwsAccount, Seq[VulnerableUser]] = {
    flaggedUsers.map { case (awsAccount, targetGroups) =>
      awsAccount -> getUsersToDisable(targetGroups.flatMap(_.users), awsAccount, dynamo)
    }
  }

  // filter the vulnerable users for those who have disablement deadlines marked as today in dynamoDB
  private def getUsersToDisable(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: Dynamo): Seq[VulnerableUser] = {
    users.filter { user =>
      val auditUsername: String =
        dynamo.getAlert(awsAccount, user.username)
          .filter(u => toDisableToday(getNearestDeadline(u.alerts)))
          .map(_.username)
          .getOrElse("")

      user.username == auditUsername
    }
  }

  def toDisableToday(deadline: DateTime, today: DateTime = DateTime.now): Boolean =
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay
}
