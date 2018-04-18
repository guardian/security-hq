package com.gu.hq

import com.gu.anghammarad.models.{App, AwsAccount, Stack, Stage}
import com.gu.hq.SecurityGroups.{NotOpen, Open, OpenELB}
import com.gu.hq.lambda.model.JSON._
import com.gu.hq.lambda.model.{InvokingEvent, Tag}
import org.scalatest.{FreeSpec, Matchers, OptionValues}
import play.api.libs.json.Json

import scala.io.Source


class NotifierTest extends FreeSpec with Matchers with OptionValues {
  import Notifier._

  "shouldNotify" - {
    "if the SG status is 'not open'" - {
      "returns None for update event" in {
        shouldNotify(updateEvent, NotOpen) shouldBe None
      }

      "returns None for non-update event" in {
        shouldNotify(noChangeEvent, NotOpen) shouldBe None
      }
    }

    "if the SG status is 'open ELB'" - {
      "returns None for update event" in {
        shouldNotify(updateEvent, OpenELB) shouldBe None
      }

      "returns None for non-update event" in {
        shouldNotify(noChangeEvent, OpenELB) shouldBe None
      }
    }

    "if the SG status is 'open'" - {
      "returns Some for update event" in {
        shouldNotify(updateEvent, Open) should not be empty
      }

      "returns None for non-update event" in {
        shouldNotify(noChangeEvent, Open) shouldBe None
      }
    }

    def updateEvent: InvokingEvent = {
      val jsonStr = Source.fromResource("config_event_with_update.json").getLines.mkString
      Json.parse(jsonStr).validate[InvokingEvent].asOpt.value
    }
    def noChangeEvent: InvokingEvent = {
      val jsonStr = Source.fromResource("config_event_no_change.json").getLines.mkString
      Json.parse(jsonStr).validate[InvokingEvent].asOpt.value
    }
  }

  "createNotification" - {
    val groupId = "sg-213"
    val targetTags = List(Tag("Stack", "stack"), Tag("App", "app"), Tag("Stage", "PROD"), Tag("Irrelevant", "foo"))
    val accountId = "123456789"
    val regionName = "eu-west-1"

    "adds AWS console CTA" - {
      "that links to AWS console" ignore {
        val notfication = createNotification(groupId, targetTags, accountId, regionName)
        notfication.actions.exists(_.url.contains("console.aws.amazon.com"))
      }

      "that searches for the provided security group" in {
        val notfication = createNotification(groupId, targetTags, accountId, regionName)
        val consoleCta = notfication.actions.find(_.url.contains("console.aws.amazon.com")).value
        consoleCta.url should include(s"search=$groupId")
      }
    }

    "adds Account ID to target" in {
      val notification = createNotification(groupId, targetTags, accountId, regionName)
      notification.target should contain(AwsAccount(accountId))
    }

    "adds stack stage and app to target" in {
      val notification = createNotification(groupId, targetTags, accountId, regionName)
      notification.target.toSet should contain allOf (Stack("stack"), App("app"), Stage("PROD"))
    }
  }
}
