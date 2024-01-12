package com.gu.hq

import com.gu.anghammarad.models.{App, AwsAccount, Stack}
import com.gu.hq.Events.{NotRelevant, Relevant}
import com.gu.hq.SecurityGroups._
import com.gu.hq.lambda.model.Tag
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class NotifierTest extends AnyFreeSpec with Matchers with OptionValues {
  import Notifier._

  "shouldNotify" - {
    "if the SG status is 'not open'" - {
      "returns None for update event" in {
        shouldNotify(Relevant, NotOpen) shouldBe None
      }

      "returns None for non-update event" in {
        shouldNotify(NotRelevant, NotOpen) shouldBe None
      }
    }

    "if the SG status is 'open ELB'" - {
      "returns None for update event" in {
        shouldNotify(Relevant, OpenELB) shouldBe None
      }

      "returns None for non-update event" in {
        shouldNotify(NotRelevant, OpenELB) shouldBe None
      }
    }

    "if the SG status is 'open'" - {
      "returns Some for update event" in {
        shouldNotify(Relevant, Open) should not be empty
      }

      "returns None for non-update event" in {
        shouldNotify(NotRelevant, Open) shouldBe None
      }
    }
  }

  "createNotification" - {
    val groupId = "sg-213"
    val targetTags = List(Tag("Stack", "stack"), Tag("App", "app"), Tag("Stage", "PROD"), Tag("Irrelevant", "foo"))
    val accountId = "123456789"
    val accountName = "abcdefg"
    val regionName = "eu-west-1"

    "adds AWS console CTA" - {
      "that links to AWS console" ignore {
        val notfication = createNotification(groupId, targetTags, accountId, accountName, regionName)
        notfication.actions.exists(_.url.contains("console.aws.amazon.com"))
      }

      "that searches for the provided security group" in {
        val notfication = createNotification(groupId, targetTags, accountId, accountName, regionName)
        val consoleCta = notfication.actions.find(_.url.contains("console.aws.amazon.com")).value
        consoleCta.url should include(s"search=$groupId")
      }
    }

    "adds Account ID to target" in {
      val notification = createNotification(groupId, targetTags, accountId, accountName, regionName)
      notification.target should contain(AwsAccount(accountId))
    }

    "adds Account Name to message" in {
      val notification = createNotification(groupId, targetTags, accountId, accountName, regionName)
      notification.message should include(accountName)
    }

    "adds Group ID to message" in {
      val notification = createNotification(groupId, targetTags, accountId, accountName, regionName)
      notification.message should include(groupId)
    }

    "adds stack and app to target" in {
      val notification = createNotification(groupId, targetTags, accountId, accountName, regionName)
      notification.target.toSet should contain allOf (Stack("stack"), App("app"))
    }

    "puts each tag on a new line" in {
      val notification = createNotification(groupId, targetTags, accountId, accountName, regionName)
      notification.message should contain
        """**Stack**: stack
           |**App**: app
           |**Stage**: PROD
           |**Irrelevant**: foo""".stripMargin
    }
  }
}
