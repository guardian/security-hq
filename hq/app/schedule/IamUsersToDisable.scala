package schedule

import model.{AwsAccount, VulnerableUser}
import org.joda.time.DateTime
import schedule.vulnerable.IamDeadline.getNearestDeadline

object IamUsersToDisable {
  def usersToDisable(flaggedUsers: Map[AwsAccount, Seq[VulnerableUser]], dynamo: DynamoAlertService, today: DateTime = DateTime.now): Map[AwsAccount, Seq[VulnerableUser]] = {
    flaggedUsers.map { case (awsAccount, users) =>
      awsAccount -> getUsersToDisable(users, awsAccount, dynamo, today)
    }
  }

  // filter the vulnerable users for those who have disablement deadlines marked as today in dynamoDB
  private def getUsersToDisable(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: DynamoAlertService, today: DateTime = DateTime.now): Seq[VulnerableUser] = {
    users.filter { user =>
      val auditUsername: Option[String] =
        dynamo.getAlert(awsAccount, user.username)
          .filter(u => toDisableToday(getNearestDeadline(u.alerts), today))
          .map(_.username)

      auditUsername.contains(user.username)
    }
  }

  private def toDisableToday(deadline: DateTime, today: DateTime = DateTime.now): Boolean =
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay
}
