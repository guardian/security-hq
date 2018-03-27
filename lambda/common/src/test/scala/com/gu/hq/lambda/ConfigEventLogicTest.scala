package com.gu.hq.lambda

import com.gu.hq.lambda.fixtures.Common
import com.gu.hq.lambda.model.Relationship
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{EitherValues, FreeSpec, Matchers, OptionValues}
import play.api.libs.json.JsNull
import fixtures.Events._
import fixtures.Diffs._
import fixtures.SecurityGroups._


class ConfigEventLogicTest extends FreeSpec with Matchers with OptionValues with EitherValues {

  "eventDetails" - {
    "parses the JSON out of an example configEvent" in {
      val invokingEvent = ConfigEventLogic.eventDetails(configEvent).value
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
      val configItemDiff = ConfigEventLogic.eventDetails(configEvent).value.configurationItemDiff.value

      configItemDiff.changeType shouldEqual "UPDATE"

      val prevVal = configItemDiff.changedProperties \ "Configuration.IpPermissions.0" \ "previousValue"
      prevVal.get shouldEqual JsNull

      val ipRanges = configItemDiff.changedProperties \ "Configuration.IpPermissions.0" \ "updatedValue" \ "ipRanges"
      ipRanges.as[List[String]].head shouldEqual "1.2.3.4/32"
    }
  }

  "sgConfiguration" - {
    "parses example configuration" in {
      val sgConf = ConfigEventLogic.sgConfiguration(sgConfigurationJson).value
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
        'fromPort (8888),
        'toPort (8888),
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
        'fromPort (22),
        'toPort (22),
        'userIdGroupPairs (Nil),
        'ipRanges (List("1.2.3.4/28")),
        'prefixListIds (Nil)
      )

      sgConf.tags.map(t => t.key -> t.value) should contain allOf("Stack" -> "stack", "App" -> "app", "Stage" -> "PROD")
    }
  }
}
