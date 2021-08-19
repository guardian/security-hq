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
        logger.info(s"found no insecure IAM users for ${awsAccount.name}. No notification required.")
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
        Some(createInsecureCredentialsNotification(warning = true, usersToReceiveWarningAlerts, awsAccount, tg.targets))
      else None
    }

    val finalNotifications =
      if (usersToReceiveFinalAlerts.nonEmpty)
        Some(createInsecureCredentialsNotification(warning = false, usersToReceiveFinalAlerts, awsAccount, tg.targets))
      else None

    IamNotification(warningNotifications, finalNotifications, users)
  }

  def createInsecureCredentialsNotification(warning: Boolean, users: Seq[VulnerableUser], awsAccount: AwsAccount, targets: List[Target]) = {
    val usersWithDeadlineAddedIfMissing = users.map { user =>
      user.copy(disableDeadline = Some(createDeadlineIfMissing(user.disableDeadline)))
    }
    val subject = if (warning) InsecureCredentials.warningSubject(awsAccount) else InsecureCredentials.finalSubject(awsAccount)
    val message = if (warning) InsecureCredentials.createWarningMessage(awsAccount, usersWithDeadlineAddedIfMissing) else InsecureCredentials.createFinalMessage(awsAccount, usersWithDeadlineAddedIfMissing)
    notification(subject, message, targets :+ Account(awsAccount.accountNumber))
  }
}
