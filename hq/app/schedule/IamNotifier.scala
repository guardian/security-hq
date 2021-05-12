package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred, Target}
import play.api.Logging
import schedule.IamMessages.{sourceSystem, subject}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal


object IamNotifier extends Logging {
  val channel = Preferred(Email)

  def createNotification(awsAccount: Target, message: String): Notification = {
    Notification(subject, message, List.empty, List(awsAccount), channel, sourceSystem)
  }

  def send(
    notification: Notification,
    topicArn: Option[String],
    snsClient: AmazonSNSAsync)(implicit executionContext: ExecutionContext): Unit = {
    logger.info(s"attempting to send iam notification to topic arn: $topicArn to targets: ${notification.target}")
    topicArn match {
      case Some(arn) =>
        val response = Anghammarad.notify(notification, arn, snsClient)
        response.onComplete{
          case Success(id) =>
            logger.info(s"Sent notification to ${notification.target}: $id")
          case Failure(err) =>
            logger.error("Failed to send notification", err)
        }
      case None => logger.error("Failed to send notification: no SNS topic provided")
    }
  }
}
