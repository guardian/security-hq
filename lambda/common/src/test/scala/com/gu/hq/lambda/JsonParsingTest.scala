package com.gu.hq.lambda

import com.gu.hq.lambda.fixtures.Common
import com.gu.hq.lambda.fixtures.Events._
import com.gu.hq.lambda.fixtures.SecurityGroups._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{EitherValues, OptionValues}
import play.api.libs.json.JsNull
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class JsonParsingTest extends AnyFreeSpec with Matchers with OptionValues with EitherValues {

  "eventDetails" - {
    "parses the JSON out of an example configEvent" in {
      val invokingEvent = JsonParsing.eventDetails(configEvent).value
      invokingEvent should have (
        'messageType ("ConfigurationItemChangeNotification"),
        'recordVersion ("1.2"),
        'notificationCreationTime (new DateTime(2016, 11, 23, 17, 20, 30, 0, DateTimeZone.UTC))
      )

      val configurationItem = invokingEvent.configurationItem.value
      configurationItem.resourceId.value shouldEqual "sg-abcdefg"
      configurationItem.configurationItemStatus.value shouldEqual "OK"

      configurationItem.relationships.size shouldEqual 3
    }

    "the diff of an example config event is useful" in {
      val configItemDiff = JsonParsing.eventDetails(configEvent).value.configurationItemDiff.value

      configItemDiff.changeType shouldEqual "UPDATE"

      val prevValEntry = configItemDiff.changedProperties.find(p => p._1 == "Configuration.IpPermissions.0").get._2
      val prevVal = prevValEntry \ "previousValue"
      prevVal.get shouldEqual JsNull

      val ipRangesEntry = configItemDiff.changedProperties.find(p => p._1 == "Configuration.IpPermissions.0").get._2
      val ipRanges = ipRangesEntry \ "updatedValue" \ "ipRanges"
      ipRanges.as[List[String]].head shouldEqual "1.2.3.4/32"
    }
  }

  "sgConfiguration" - {
    "parses example configuration" in {
      val sgConf = JsonParsing.sgConfiguration(sgConfigurationJson).value
      sgConf should have (
        'ownerId (Common.accountId),
        'groupName ("app-InstanceSecurityGroup-ABCDEFG"),
        'groupId ("sg-abcdefg"),
        'description ("description of security group"),
        'vpcId ("vpc-0123456")
      )

      val firstIpEntry = sgConf.ipPermissions.head
      firstIpEntry should have (
        'ipProtocol ("tcp"),
        'fromPort (Some(8888)),
        'toPort (Some(8888)),
        'ipRanges (Nil),
        'prefixListIds (Nil)
      )
      firstIpEntry.userIdGroupPairs.head should have (
        'userId ("987654321"),
        'groupId ("sg-gfedcba"),
        'groupName (None),
        'vpcId (None),
        'vpcPeeringConnectionId (None),
        'peeringStatus (None)
      )

      val secondIpEntry = sgConf.ipPermissions(1)
      secondIpEntry should have (
        'ipProtocol ("tcp"),
        'fromPort (Some(22)),
        'toPort (Some(22)),
        'userIdGroupPairs (Nil),
        'ipRanges (List("1.2.3.4/28")),
        'prefixListIds (Nil)
      )

      sgConf.tags.map(t => t.key -> t.value) should contain allOf("Stack" -> "stack", "App" -> "app", "Stage" -> "PROD")
    }
  }

  "account mapping configuration" - {
    "account maps correctly" in {
      val mapping = JsonParsing.accountMapping("""{ "a": "b", "c": "d" }""").get
      mapping.get("c") should be (Some("d"))
    }
    "missing account doesn't map" in {
      val mapping = JsonParsing.accountMapping("""{ "a": "b", "c": "d"  }""").get
      mapping.get("e") should be (None)
    }
  }
}
