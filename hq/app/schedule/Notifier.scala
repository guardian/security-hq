package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{AwsAccount => _, _}
import play.api.Logging
import schedule.IamMessages.sourceSystem
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object Notifier extends Logging {
  val channel = Preferred(Email)

  def notification(subject: String, message: String, targets: List[Target]): Notification =
    Notification(subject, message, List.empty, targets, channel, sourceSystem)

  def send(
    notification: Notification,
    topicArn: Option[String],
    snsClient: AmazonSNSAsync,
    testMode: Boolean
  )(implicit executionContext: ExecutionContext): Attempt[String] = {
    logger.info(s"attempting to send iam notification to topic arn: $topicArn to targets: ${notification.target}")
    Attempt{
      topicArn match {
      case Some(arn) =>
        val anghammaradNotification = {
          if (testMode) notification.copy(target = List(Stack("testing-alerts"))) else notification
        }
        val response: Future[String] = Anghammarad.notify(anghammaradNotification, arn, snsClient)
        response.transformWith {
          case Success(id) =>
            logger.info(s"Sent notification to ${anghammaradNotification.target}: $id")
            Future(Right(id))
          case Failure(err) =>
            Future(Left(FailedAttempt(utils.attempt.Failure("", "", 1, None, Some(err)))))
        }
      case None =>
        logger.error("Failed to send notification: no SNS topic provided")
        Future(Left(FailedAttempt(utils.attempt.Failure("", "", 1, None, None))))
      }
    }
  }
}
