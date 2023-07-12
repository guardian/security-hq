package com.gu.hq

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models._
import com.gu.hq.Events.{NotRelevant, Relevance, Relevant}
import com.gu.hq.SecurityGroups._
import com.gu.hq.lambda.model.Tag
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal


object Notifier extends StrictLogging {
  private val subject = "Open Security Group Notification"
  private val sourceSystem = "Security HQ - Security Groups Lambda"
  private val channel = Preferred(HangoutsChat)  // use hangouts if available, but email is ok too

  def shouldNotify(relevance: Relevance, status: SgStatus): Option[Unit] = {
    relevance match {
      case NotRelevant => None
      case Relevant =>
        status match {
          case NotOpen => None
          case OpenELB => None
          case Open => Some(())
        }
    }
  }

  def createNotification(groupId: String, targetTags: List[Tag], accountId: String, accountName:String, regionName: String): Notification = {
    val actions = List(
      Action("View in AWS Console", s"https://$regionName.console.aws.amazon.com/ec2/v2/home?region=$regionName#SecurityGroups:search=$groupId")
    )
    val message =
      s"""
         |Warning: Security group **$groupId** in account **$accountName** is open to the world.
         |
         |The security group has ${targetTags.length} tags.
         |${targetTags.map(t => s"**${t.key}**: ${t.value}").mkString(", ")}
         |
         |""".stripMargin
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
    List(stack, app, Some(AwsAccount(account))).flatten
  }
}
