package notifications

import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred, Target, AwsAccount => Account}
import config.Config.{daysBetweenFinalNotificationAndRemediation, daysBetweenWarningAndFinalNotification}
import logic.DateUtils.printDay
import model.{AwsAccount, HumanUser, IAMUser, Tag}
import org.joda.time.DateTime
import play.api.Logging
import utils.attempt.{Attempt, Failure}

import software.amazon.awssdk.services.sns.SnsAsyncClient
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal


object AnghammaradNotifications extends Logging {
  def send(
    notification: Notification,
    topicArn: String,
    snsClient: SnsAsyncClient,
  )(implicit executionContext: ExecutionContext): Attempt[String] = {
    Attempt.fromFuture(Anghammarad.notify(notification, topicArn, snsClient)) { case NonFatal(e) =>
      Failure(
        s"Failed to send Anghammarad notification ${e.getMessage}",
        "Unable to send developer notification",
        500,
        throwable = Some(e)
      ).attempt
    }.tap {
      case Left(failure) =>
        logger.error(failure.logMessage, failure.firstException.orNull)
      case Right(id) =>
        logger.info(s"Sent notification to ${notification.target}: $id")
    }
  }

  val channel = Preferred(Email)
  val sourceSystem = "Security HQ Credentials Notifier"

  private def notificationTargets(awsAccount: AwsAccount, iamUser: IAMUser): List[Target] =
    Tag.tagsToAnghammaradTargets(iamUser.tags) :+ Account(awsAccount.accountNumber)

  def outdatedCredentialWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime, now: DateTime): Notification = {
    val deadline = printDay(now.plusDays(daysBetweenWarningAndFinalNotification + daysBetweenFinalNotificationAndRemediation))
    val message =
      s"""
         |Please check the permanent credential ${iamUser.username} in AWS Account ${awsAccount.name},
         |which has been flagged because it was last rotated on ${printDay(problemCreationDate)}.
         |(if you're already planning on doing this, please ignore this message).
         |If this is not rectified before $deadline,
         |Security HQ will automatically disable this user at the next opportunity.
         |""".stripMargin
    val subject = s"Action required by $deadline: long-lived credential detected in ${awsAccount.name}"
    Notification(subject, message + genericOutdatedCredentialText, Nil, notificationTargets(awsAccount, iamUser), channel, sourceSystem)
  }

  def outdatedCredentialFinalWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime, now: DateTime): Notification = {
    val deadline = printDay(now.plusDays(daysBetweenFinalNotificationAndRemediation))
    val message =
      s"""
         |Please check the permanent credential ${iamUser.username} in AWS Account ${awsAccount.name},
         |which has been flagged because it was last rotated on ${printDay(problemCreationDate)}.
         |(if you're already planning on doing this, please ignore this message).
         |If this is not rectified before $deadline,
         |Security HQ will automatically disable this user at the next opportunity.
         |""".stripMargin
    val subject = s"Action required by $deadline: long-lived credential in ${awsAccount.name} will be disabled soon"
    Notification(subject, message + genericOutdatedCredentialText, Nil, notificationTargets(awsAccount, iamUser), channel, sourceSystem)
  }

  def outdatedCredentialRemediation(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    val message =
      s"""
         |The permanent credential, ${iamUser.username}, in ${awsAccount.name} was disabled today,
         |because it was last rotated on ${printDay(problemCreationDate)}.
         |If you still require the disabled user, add new access keys(s) and rotate regularly. Otherwise, delete them.
         |""".stripMargin
    val subject = s"DISABLED long-lived credential in ${awsAccount.name}"
    Notification(subject, message + genericOutdatedCredentialText, Nil, notificationTargets(awsAccount, iamUser), channel, sourceSystem)
  }

  private val genericOutdatedCredentialText = {
    s"""
       |To find out how to rectify this, see Grafana (https://metrics.gutools.co.uk/d/bdn97cui5rbi8f/iam-credentials-report?orgId=1).
       |Here is some helpful documentation on:
       |rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html,
       |deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console,
       |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
       |""".stripMargin
  }

  def unrecognisedUserRemediation(awsAccount: AwsAccount, iamUser: IAMUser): Notification = {
    val message =
      s"""
         |The permanent credential, ${iamUser.username}, in ${awsAccount.name} was disabled today.
         |Please check Grafana to review the IAM users in your account (https://metrics.gutools.co.uk/d/bdn97cui5rbi8f/iam-credentials-report?orgId=1).
         |If you still require the disabled user, ensure they are tagged correctly with their Google username
         |and have an entry in Janus.
         |If the disabled user has left the organisation, they should be deleted.
         |If you have any questions, contact devx@theguardian.com.
         |""".stripMargin
    val subject = s"AWS IAM User ${iamUser.username} DISABLED in ${awsAccount.name} Account"
    Notification(subject, message, Nil, notificationTargets(awsAccount, iamUser), channel, sourceSystem)
  }
}
