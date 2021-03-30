package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred, Target}
import model.CredentialReportDisplay
import play.api.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal


object CredentialsNotifier extends Logging {
  val subject = "Action required - old AWS credentials and/or credentials missing MFA"
  val channel = Preferred(Email)
  val sourceSystem = "Security HQ Credentials Notifier"

  //TODO write multi-line string
  // https://github.com/guardian/frontend/blob/9468d4bc3e35dc9213f8f5c18d4b88c70b8667cc/common/app/services/Notification.scala#L71
  //and write tests for this
  def createMessage(outdatedKeys: CredentialReportDisplay, missingMfa: CredentialReportDisplay): String = ???

  def createNotification(awsAccount: Target, message: String): Notification = {
    Notification(subject, message, List.empty, List(awsAccount), channel, sourceSystem)
  }

  def send(
    notification: Notification,
    topicArn: String,
    snsClient: AmazonSNSAsync)(implicit executionContext: ExecutionContext): Unit = {

    val response = Anghammarad.notify(notification, topicArn, snsClient)

    try {
      val id = Await.result(response, 5.seconds)
      logger.info(s"Sent notification to ${notification.target}: $id")
    } catch {
      case NonFatal(err) =>
        logger.error("Failed to send notification", err)
    }
  }
}
