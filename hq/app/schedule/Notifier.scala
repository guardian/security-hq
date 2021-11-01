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

  //  def handleFuture[A](future: Future[A], label: String)(implicit ec: ExecutionContext): Attempt[A] = Attempt.fromFuture(future) {
  //    case NonFatal(e) =>
  //      val failure = Failure(e.getMessage, s"Could not read $label from Snyk", 502, None, Some(e))
  //      FailedAttempt(failure)
  //  }

  def send(
    notification: Notification,
    topicArn: String,
    snsClient: AmazonSNSAsync,
    testMode: Boolean
  )(implicit executionContext: ExecutionContext): Attempt[String] = {
    Attempt.fromFuture {
      val anghammaradNotification = if (testMode) notification.copy(target = List(Stack("testing-alerts"))) else notification
      Anghammarad.notify(anghammaradNotification, topicArn, snsClient)
    } {
      case NonFatal(e) =>
        FailedAttempt(Failure(
          "unable to send anghammarad notification",
          "I haven't been able to send this anghammarad notification",
          500,
          context = Some(e.getMessage),
          throwable = Some(e)
        ))
    }

//    try {
//      Attempt.Right {
//        val anghammaradNotification = if (testMode) notification.copy(target = List(Stack("testing-alerts"))) else notification
//        Anghammarad.notify(anghammaradNotification, topicArn, snsClient)
//      }
//    } catch {
//      case NonFatal(e) =>
//        Attempt.Left(FailedAttempt(Failure(
//          "unable to send anghammarad notification",
//          "I haven't been able to send this anghammarad notification",
//          500,
//          context = Some(e.getMessage),
//          throwable = Some(e)
//        )))
//    }
  }
}

