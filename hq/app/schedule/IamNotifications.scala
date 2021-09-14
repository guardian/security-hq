package schedule

import com.gu.anghammarad.models.{Notification, Target, AwsAccount => Account}
import model._
import org.joda.time.DateTime
import play.api.Logging
import schedule.IamMessages._
import schedule.Notifier.notification
import schedule.vulnerable.IamDeadline.{createDeadlineIfMissing, sortUsersIntoWarningOrFinalAlerts}

object IamNotifications extends Logging {

  def makeNotification(flaggedCredsToNotify: Map[AwsAccount, Seq[IAMAlertTargetGroup]]): List[IamNotification] = {
    flaggedCredsToNotify.toList.flatMap { case (awsAccount, targetGroups) =>
      if (targetGroups.isEmpty) {
        logger.info(s"found no vulnerable IAM users for ${awsAccount.name}. No notification required.")
        None
      } else {
        targetGroups.map(tg => createWarningAndFinalNotification(tg, awsAccount, createIamAuditUsers(tg.users, awsAccount)))
      }
    }
  }

  private def createIamAuditUsers(users: Seq[VulnerableUser], account: AwsAccount): Seq[IamAuditUser] = {
    users.map { user =>
      IamAuditUser(
        Dynamo.createId(account, user.username), account.name, user.username,
        List(IamAuditAlert(VulnerableCredential, DateTime.now, createDeadlineIfMissing(user.disableDeadline)))
      )
    }
  }

  private def createWarningAndFinalNotification(tg: IAMAlertTargetGroup, awsAccount: AwsAccount, users: Seq[IamAuditUser]): IamNotification = {
    logger.info(s"for ${awsAccount.name}, generating iam notification message for ${tg.users.length} user(s) with outdated keys and and/or missing mfa")
    val (usersToReceiveWarningAlerts, usersToReceiveFinalAlerts) = sortUsersIntoWarningOrFinalAlerts(tg.users)

    val warningNotifications = {
      if (usersToReceiveWarningAlerts.nonEmpty)
        Some(createVulnerableCredentialsNotification(warning = true, usersToReceiveWarningAlerts, awsAccount, tg.targets))
      else None
    }

    val finalNotifications =
      if (usersToReceiveFinalAlerts.nonEmpty)
        Some(createVulnerableCredentialsNotification(warning = false, usersToReceiveFinalAlerts, awsAccount, tg.targets))
      else None

    IamNotification(warningNotifications, finalNotifications, users)
  }

  private def createVulnerableCredentialsNotification(warning: Boolean, users: Seq[VulnerableUser], awsAccount: AwsAccount, targets: List[Target]): Notification = {
    val usersWithDeadlineAddedIfMissing = users.map { user =>
      user.copy(disableDeadline = Some(createDeadlineIfMissing(user.disableDeadline)))
    }
    val subject = if (warning) VulnerableCredentials.warningSubject(awsAccount) else VulnerableCredentials.finalSubject(awsAccount)
    val message = if (warning) VulnerableCredentials.createWarningMessage(awsAccount, usersWithDeadlineAddedIfMissing) else VulnerableCredentials.createFinalMessage(awsAccount, usersWithDeadlineAddedIfMissing)
    notification(subject, message, targets :+ Account(awsAccount.accountNumber))
  }
}
