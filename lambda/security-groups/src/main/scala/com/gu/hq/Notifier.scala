package com.gu.hq

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models._
import com.gu.hq.lambda.model.Tag
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal


object Notifier extends StrictLogging {
  private val subject = "Open Security Group Notification"
  private val sourceSystem = "Security HQ - Security Groups Lambda"
  private val channel = HangoutsChat

  def send(
    groupId: String,
    accountName: String,
    targetTags: List[Tag],
    account: String,
    arn: String,
    client: AmazonSNSAsync): Unit = {

    val actions = List(Action("View in Security HQ", s"https://security-hq.gutools.co.uk/security-groups/$accountName"))
    val message = s"Warning: Security group '$groupId' in account '$accountName' is open to the world"
    val targets = getTargetsFromTags(targetTags, account)

    val result = Anghammarad.notify(
      subject,
      message,
      sourceSystem,
      channel,
      targets,
      actions,
      arn,
      client
    )
    try {
      val id = Await.result(result, 5.seconds)
      logger.info(s"Sent notification to $targets: $id")
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
