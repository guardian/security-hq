package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import model._
import org.joda.time.DateTime
import schedule.IamMessages.{disabledUsersMessage, disabledUsersSubject}
import schedule.IamNotifier.notification
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

object IamTestEmailFormatting {

  sealed trait NotificationType
  object Warning extends NotificationType
  object Final extends NotificationType
  object Disabled extends NotificationType

  //emails are sent to: https://groups.google.com/a/guardian.co.uk/g/anghammarad.test.alerts
  def sendTestNotification(snsClient: AmazonSNSAsync, topicArn: Option[String], notificationType: NotificationType)(implicit ec: ExecutionContext): Attempt[String] = {
    val account = AwsAccount("", "Test", "", "123456")
    val users: Seq[VulnerableUser] = Seq(
      VulnerableUser(
        "test-user 1",
        AccessKey(AccessKeyEnabled, Some(DateTime.now.minusMonths(4))),
        AccessKey(AccessKeyDisabled, Some(DateTime.now.minusMonths(4))),
        true,
        List.empty
      ),
      VulnerableUser(
        "test-user 2",
        AccessKey(NoKey, None),
        AccessKey(AccessKeyDisabled, Some(DateTime.now.minusMonths(5))),
        true,
        List.empty
      )
    )
    IamNotifier.send(sendCorrectNotification(notificationType, users, account), topicArn, snsClient, testMode = true)
  }

  def sendCorrectNotification(notificationType: NotificationType, users: Seq[VulnerableUser], account: AwsAccount): Notification = notificationType match {
    case Warning => IamNotifications.createNotification(true, users, account, List.empty)
    case Final => IamNotifications.createNotification(false, users, account, List.empty)
    case Disabled => notification(disabledUsersSubject(account), disabledUsersMessage(users), List(Account(account.accountNumber)))
  }

}
