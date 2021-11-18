package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{AwsAccount => _, _}
import play.api.Logging
import schedule.IamMessages.sourceSystem
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal


object Notifier extends Logging {
  val channel = Preferred(Email)

  def notification(subject: String, message: String, targets: List[Target]): Notification =
    Notification(subject, message, List.empty, targets, channel, sourceSystem)

  def send(
    notification: Notification,
    topicArn: String,
    snsClient: AmazonSNSAsync,
    testMode: Boolean
  )(implicit executionContext: ExecutionContext): Attempt[String] = {
    logger.info(s"attempting to send iam notification to topic arn: $topicArn to targets: ${notification.target}")
    val anghammaradNotification = {
      if (testMode) notification.copy(target = List(Stack("testing-alerts")))
      else notification
    }
    Attempt.fromFuture(Anghammarad.notify(anghammaradNotification, topicArn, snsClient)){
      case NonFatal(e) =>
        FailedAttempt(Failure(e.getMessage, s"Failed to send Anghammarad notification", 500, None, Some(e)))
    }
  }
}
