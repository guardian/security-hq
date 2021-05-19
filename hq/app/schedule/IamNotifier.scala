package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred, Target}
import model._
import org.joda.time.DateTime
import play.api.Logging
import schedule.IamMessages.{sourceSystem, subject}
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object IamNotifier extends Logging {
  val channel = Preferred(Email)

  def createNotification(
    accountName: AwsAccount,
    targets: List[Target],
    message: String,
    awsAccountId: String,
    username: String,
    alertType: IamAuditNotificationType = Warning
  ): IamNotification = {
    val alerts: List[IamAuditAlert] = List(IamAuditAlert(DateTime.now, alertType))
    val iamAuditUser: IamAuditUser = IamAuditUser(awsAccountId, accountName.name, username, alerts)
    val anghammaradNotification = Notification(subject(accountName), message, List.empty, targets, channel, sourceSystem)
    IamNotification(iamAuditUser, anghammaradNotification)
  }

  def send(
    notification: IamNotification,
    topicArn: Option[String],
    snsClient: AmazonSNSAsync)(implicit executionContext: ExecutionContext): Attempt[String] = {
    logger.info(s"attempting to send iam notification to topic arn: $topicArn to targets: ${notification.anghammaradNotification.target}")
    Attempt{
      topicArn match {
      case Some(arn) =>
        val response: Future[String] = Anghammarad.notify(notification.anghammaradNotification, arn, snsClient)
        response.transformWith {
          case Success(id) =>
            logger.info(s"Sent notification to ${notification.anghammaradNotification.target}: $id")
            Future(Right(id))
          case Failure(err) =>
            logger.error("Failed to send notification", err)
            Future(Left(FailedAttempt(utils.attempt.Failure("", "", 1, None, Some(err)))))
        }
      case None =>
        logger.error("Failed to send notification: no SNS topic provided")
        Future(Left(FailedAttempt(utils.attempt.Failure("", "", 1, None, None))))
      }
    }
  }
}
