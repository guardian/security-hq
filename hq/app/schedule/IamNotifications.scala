package schedule

import com.gu.anghammarad.models.{Target, AwsAccount => Account}
import model._
import org.joda.time.DateTime
import play.api.Logging
import schedule.IamDeadline.{createDeadlineIfMissing, sortUsersIntoWarningOrFinalAlerts}
import schedule.IamMessages._
import schedule.IamNotifier.notification

object IamNotifications extends Logging {

  def makeNotification(flaggedCredsToNotify: Map[AwsAccount, Seq[IAMAlertTargetGroup]]): List[IamNotification] = {
    flaggedCredsToNotify.toList.flatMap { case (awsAccount, targetGroups) =>
      if (targetGroups.isEmpty) {
        logger.info(s"found no IAM user issues for ${awsAccount.name}. No notification required.")
        None
      } else {
        targetGroups.map(tg => createWarningAndFinalNotification(tg, awsAccount, createIamAuditUsers(tg.users, awsAccount)))
      }
    }
  }

  def createIamAuditUsers(users: Seq[VulnerableUser], account: AwsAccount): Seq[IamAuditUser] = {
    users.map { user =>
      IamAuditUser(
        Dynamo.createId(account, user.username), account.name, user.username,
        List(IamAuditAlert(DateTime.now, createDeadlineIfMissing(user.disableDeadline)))
      )
    }
  }

  def createWarningAndFinalNotification(tg: IAMAlertTargetGroup, awsAccount: AwsAccount, users: Seq[IamAuditUser]): IamNotification = {
    logger.info(s"for ${awsAccount.name}, generating iam notification message for ${tg.users.length} user(s) with outdated keys and and/or missing mfa")
    val (usersToReceiveWarningAlerts, usersToReceiveFinalAlerts) = sortUsersIntoWarningOrFinalAlerts(tg.users)

    val warningNotifications = {
      if (usersToReceiveWarningAlerts.nonEmpty)
        Some(createNotification(warning = true, usersToReceiveWarningAlerts, awsAccount, tg.targets))
      else None
    }

    val finalNotifications =
      if (usersToReceiveFinalAlerts.nonEmpty)
        Some(createNotification(warning = false, usersToReceiveFinalAlerts, awsAccount, tg.targets))
      else None

    IamNotification(warningNotifications, finalNotifications, users)
  }

  def createNotification(warning: Boolean, users: Seq[VulnerableUser], awsAccount: AwsAccount, targets: List[Target]) = {
    val usersWithDeadlineAddedIfMissing = users.map { user =>
      user.copy(disableDeadline = Some(createDeadlineIfMissing(user.disableDeadline)))
    }
    val subject = if (warning) warningSubject(awsAccount) else finalSubject(awsAccount)
    val message = if (warning) createWarningMessage(awsAccount, usersWithDeadlineAddedIfMissing) else createFinalMessage(awsAccount, usersWithDeadlineAddedIfMissing)
    notification(subject, message, targets :+ Account(awsAccount.accountNumber))
  }
}
