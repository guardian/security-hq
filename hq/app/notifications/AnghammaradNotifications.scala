package notifications

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred, AwsAccount => Account}
import config.Config.{daysBetweenFinalNotificationAndRemediation, daysBetweenWarningAndFinalNotification}
import logic.DateUtils.printDay
import model.{AwsAccount, IAMUser}
import org.joda.time.DateTime
import play.api.Logging
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal


object AnghammaradNotifications extends Logging {
  def send(
    notification: Notification,
    topicArn: String,
    snsClient: AmazonSNSAsync,
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

  def outdatedCredentialWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime, now: DateTime): Notification = {
    val message =
      s"""
         |Please check the permanent credential ${iamUser.username} in AWS Account ${awsAccount.name},
         |which has been flagged because it was last rotated on ${printDay(problemCreationDate)}.
         |(if you're already planning on doing this, please ignore this message).
         |If this is not rectified before ${printDay(now.plusDays(daysBetweenWarningAndFinalNotification))}, Security HQ will automatically disable this user."
         |""".stripMargin
    val subject = s"Action ${awsAccount.name}: Vulnerable credential"
    Notification(subject, message + genericOutdatedCredentialText, Nil, List(Account(awsAccount.accountNumber)), channel, sourceSystem)
  }

  def passwordWithoutMfaWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  def outdatedCredentialFinalWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime, now: DateTime): Notification = {
    val message =
      s"""
         |Please check the permanent credential ${iamUser.username} in AWS Account ${awsAccount.name},
         |which has been flagged because it was last rotated on ${printDay(problemCreationDate)}.
         |(if you're already planning on doing this, please ignore this message).
         |If this is not rectified before ${printDay(now.plusDays(daysBetweenFinalNotificationAndRemediation))}, Security HQ will automatically disable this user."
         |""".stripMargin
    val subject = s"Action ${awsAccount.name}: Vulnerable credential will be disabled soon"
    Notification(subject, message + genericOutdatedCredentialText, Nil, List(Account(awsAccount.accountNumber)), channel, sourceSystem)
  }

  def passwordWithoutMfaFinalWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  def outdatedCredentialRemediation(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    val message =
      s"""
         |The permanent credential, ${iamUser.username}, in ${awsAccount.name} was disabled today,
         |because it was last rotated on ${printDay(problemCreationDate)}.
         |If you still require the disabled user, add new access keys(s) and rotate regularly. Otherwise, delete them.
         |""".stripMargin
    val subject = s"DISABLED Vulnerable credential in ${awsAccount.name}"
    Notification(subject, message + genericOutdatedCredentialText, Nil, List(Account(awsAccount.accountNumber)), channel, sourceSystem)
  }

  def passwordWithoutMfaRemediation(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  private val genericOutdatedCredentialText = {
    s"""
       |To find out how to rectify this, see Security HQ (https://security-hq.gutools.co.uk/iam).
       |Here is some helpful documentation on:
       |rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html,
       |deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console,
       |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
       |""".stripMargin
  }
}
