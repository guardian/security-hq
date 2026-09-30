package com.gu.guardianduty

import com.amazonaws.services.lambda.runtime.Context
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.Notification
import software.amazon.awssdk.services.sns.SnsAsyncClient

import scala.concurrent.{ExecutionContext, Future}

trait Notifications {
  def notify(notification: Notification): Future[String]
}
class AnghammaradNotifications(
    topicArn: String,
    // optional SNS client to allow faster cold starts in Lambda
    maybeSnsClient: Option[SnsAsyncClient]
)(using ExecutionContext)
    extends Notifications {
  override def notify(notification: Notification): Future[String] = {
    maybeSnsClient match {
      case Some(snsClient) =>
        Anghammarad.notify(notification, topicArn, snsClient)
      case None =>
        Anghammarad.notify(notification, topicArn)
    }
  }
}
class ConsoleNotifications extends Notifications {
  override def notify(notification: Notification): Future[String] = {
    println(s"-------------------------------------------------------------")
    println(s"${notification.subject}")
    println(s"-------------------------------------------------------------")
    println(notification.message)
    println(s"-------------------------------------------------------------")
    notification.actions.foreach(a => println(s"=> ${a.cta}: ${a.url}"))
    println(s"-------------------------------------------------------------")
    Future.successful("log-message-id")
  }
}

trait Logging {
  def log(message: String): Unit
}
class LambdaLogger(context: Context) extends Logging {
  override def log(message: String): Unit = context.getLogger.log(message)
}
class ConsoleLogger extends Logging {
  override def log(message: String): Unit = println(message)
}
class NoOpLogger extends Logging {
  override def log(message: String): Unit = ()
}

/** Bundle of all service dependencies for the core processing function. */
case class Services(
    notifications: Notifications,
    logger: Logging
)
