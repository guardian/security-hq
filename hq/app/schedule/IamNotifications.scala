package schedule

import com.gu.anghammarad.models.{Notification, Target, AwsAccount => Account}
import model._
import org.joda.time.DateTime
import play.api.Logging
import schedule.IamMessages._
import schedule.IamTargetGroups.getNotificationTargetGroups
import schedule.Notifier.notification

object IamNotifications extends Logging {

//  def makeNotifications(flaggedCredsToNotify: Map[AwsAccount, Seq[VulnerableUser]]): List[IamNotification] = {
//    flaggedCredsToNotify.toList.flatMap { case (awsAccount, users) =>
//      if (users.isEmpty) {
//        logger.info(s"found no vulnerable IAM users for ${awsAccount.name}. No notification required.")
//        None
//      } else {
//        // TODO: can we just apply getNotificationTargetGroups for all accounts?
//        val targetGroupsFromTags: Seq[IAMAlertTargetGroup] =
//          if(awsAccount.name == "Ophan") getNotificationTargetGroups(users)
//          else Seq(IAMAlertTargetGroup(List.empty, users))
//
//        targetGroupsFromTags.map(tg => {
//          // Always include the account as a notification target
//          val targetGroupWithAccounts = tg.copy(targets = tg.targets :+ Account(awsAccount.accountNumber))
//          val iamAuditUsers = createIamAuditUsers(targetGroupWithAccounts.users, awsAccount)
//          createWarningAndFinalNotification(targetGroupWithAccounts, awsAccount, iamAuditUsers)
//        })
//      }
//    }
//  }


//  private def createIamAuditUsers(users: Seq[VulnerableUser], account: AwsAccount): Seq[IamAuditUser] = {
//    users.map { user =>
//      IamAuditUser(
//        DynamoAlerts.createId(account, user.username), account.name, user.username,
//        List(IamAuditAlert(VulnerableCredential, DateTime.now, createDeadlineIfMissing(user.disableDeadline)))
//      )
//    }
//  }

//  private def createWarningAndFinalNotification(tg: IAMAlertTargetGroup, awsAccount: AwsAccount, users: Seq[IamAuditUser]): IamNotification = {
//    logger.info(s"for ${awsAccount.name}, generating iam notification message for ${tg.users.length} user(s) with outdated keys and and/or missing mfa")
//    val (usersToReceiveWarningAlerts, usersToReceiveFinalAlerts) = sortUsersIntoWarningOrFinalAlerts(tg.users)
//
//    val warningNotifications = {
//      if (usersToReceiveWarningAlerts.nonEmpty)
//        Some(createVulnerableCredentialsNotification(warning = true, usersToReceiveWarningAlerts, awsAccount, tg.targets))
//      else None
//    }
//
//    val finalNotifications =
//      if (usersToReceiveFinalAlerts.nonEmpty)
//        Some(createVulnerableCredentialsNotification(warning = false, usersToReceiveFinalAlerts, awsAccount, tg.targets))
//      else None
//
//    IamNotification(warningNotifications, finalNotifications, users)
//  }

//  // TODO: this should be private as it's only used by tests and internally
//  def createVulnerableCredentialsNotification(warning: Boolean, users: Seq[VulnerableUser], awsAccount: AwsAccount, targets: List[Target]): Notification = {
//    val usersWithDeadlineAddedIfMissing = users.map { user =>
//      user.copy(disableDeadline = Some(createDeadlineIfMissing(user.disableDeadline)))
//    }
//    val subject = if (warning) VulnerableCredentials.warningSubject(awsAccount) else VulnerableCredentials.finalSubject(awsAccount)
//    val message = if (warning) VulnerableCredentials.createWarningMessage(awsAccount, usersWithDeadlineAddedIfMissing) else VulnerableCredentials.createFinalMessage(awsAccount, usersWithDeadlineAddedIfMissing)
//    notification(subject, message, targets)
//  }
}
