package notifications

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred}
import play.api.Logging
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal


object AnghammaradNotifications extends Logging {
  val channel = Preferred(Email)

  def send(
    notification: Notification,
    topicArn: String,
    snsClient: AmazonSNSAsync,
  )(implicit executionContext: ExecutionContext): Attempt[String] = {
    Attempt.fromFuture(Anghammarad.notify(notification, topicArn, snsClient)) { case NonFatal(e) =>
      Failure(s"Failed to send Anghammarad notification ${e.getMessage}", "Unable to send developer notification", 500).attempt
    }.tap {
      case Left(failure) =>
        logger.error(failure.logMessage, failure.firstException.orNull)
      case Right(id) =>
        logger.info(s"Sent notification to ${notification.target}: $id")
    }
  }
}
