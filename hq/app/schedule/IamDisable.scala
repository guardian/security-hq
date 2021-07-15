package schedule

import model.{AwsAccount, IamAuditUser, VulnerableUser}
import org.joda.time.DateTime
import play.api.Logging
import schedule.IamDeadline.getNearestDeadline

object IamDisable extends Logging {

  def getUsersToDisable(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: Dynamo): Seq[IamAuditUser] = {
    users.flatMap{ user =>
      dynamo.getAlert(awsAccount, user.username).filter(u => toDisableToday(getNearestDeadline(u.alerts)))
    }
  }

  def toDisableToday(deadline: DateTime, today: DateTime = DateTime.now): Boolean =
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay
}
