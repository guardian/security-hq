package com.gu.hq

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models._
import com.gu.hq.SecurityGroups.{NotOpen, Open, OpenELB, SgStatus}
import com.gu.hq.lambda.model.{InvokingEvent, Tag}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal


object Notifier extends StrictLogging {
  private val subject = "Open Security Group Notification"
  private val sourceSystem = "Security HQ - Security Groups Lambda"
  private val channel = Preferred(HangoutsChat)  // use hangouts if available, but email is ok too

  def shouldNotify(invokingEvent: InvokingEvent, status: SgStatus): Option[Unit] = {
    status match {
      case NotOpen =>
        None
      case OpenELB =>
        None
      case Open =>
        val isRelevantChange = invokingEvent.configurationItemDiff.exists { diff =>
          Set("CREATE", "UPDATE").contains(diff.changeType)
        }
        if (isRelevantChange) Some(())
        else None
    }
  }

  def createNotification(groupId: String, targetTags: List[Tag], accountId: String, accountName:String, regionName: String): Notification = {
    val actions = List(
      Action("View in AWS Console", s"https://$regionName.console.aws.amazon.com/ec2/v2/home?region=$regionName#SecurityGroups:search=$groupId")
    )
    val message = s"Warning: Security group '$groupId' in account '$accountName' is open to the world"
    val targets = getTargetsFromTags(targetTags, accountId)

    Notification(subject, message, actions, targets, channel, sourceSystem)
  }

  def send(
    notification: Notification,
    topicArn: String,
    snsClient: AmazonSNSAsync): Unit = {

    val result = Anghammarad.notify(notification, topicArn, snsClient)

    try {
      val id = Await.result(result, 5.seconds)
      logger.info(s"Sent notification to ${notification.target}: $id")
    } catch {
      case NonFatal(err) =>
        logger.error("Failed to send notification", err)
    }
  }

  private def getTargetsFromTags(tags: List[Tag], account: String):List[Target] = {
    val stack = tags.find(t => t.key.equals("Stack")).map(t => Stack(t.value))
    val app = tags.find(t => t.key.equals("App")).map(t => App(t.value))
    val stage = tags.find(t => t.key.equals("Stage")).map(t => Stage(t.value))
    List(stack, app, stage, Some(AwsAccount(account))).flatten
  }
}
