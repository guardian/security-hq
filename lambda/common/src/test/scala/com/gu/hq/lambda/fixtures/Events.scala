package com.gu.hq.lambda.fixtures

import com.amazonaws.services.lambda.runtime.events.ConfigEvent


object Events {
  import Common.accountId

  val configRuleArn = s"arn:aws:config:eu-west-1:$accountId:config-rule/config-rule-abcdefg"
  val invokingEventJson = s"""{
                             |    "configurationItemDiff": {
                             |        "changedProperties": {
                             |            "Configuration.IpPermissions.0": {
                             |                "previousValue": null,
                             |                "updatedValue": {
                             |                    "ipProtocol": "tcp",
                             |                    "fromPort": 443,
                             |                    "toPort": 443,
                             |                    "userIdGroupPairs": [],
                             |                    "ipRanges": [
                             |                        "1.2.3.4/32"
                             |                    ],
                             |                    "prefixListIds": []
                             |                },
                             |                "changeType": "CREATE"
                             |            }
                             |        },
                             |        "changeType": "UPDATE"
                             |    },
                             |    "configurationItem": {
                             |        "relatedEvents": [],
                             |        "relationships": [
                             |            {
                             |                "resourceId": "eni-abcdefg",
                             |                "resourceName": null,
                             |                "resourceType": "AWS::EC2::NetworkInterface",
                             |                "name": "Is associated with NetworkInterface"
                             |            },
                             |            {
                             |                "resourceId": "i-abcdefg",
                             |                "resourceName": null,
                             |                "resourceType": "AWS::EC2::Instance",
                             |                "name": "Is associated with Instance"
                             |            },
                             |            {
                             |                "resourceId": "vpc-abcdefg",
                             |                "resourceName": null,
                             |                "resourceType": "AWS::EC2::VPC",
                             |                "name": "Is contained in Vpc"
                             |            }
                             |        ],
                             |        "configuration": {
                             |            "ownerId": "$accountId",
                             |            "groupName": "app-InstanceSecurityGroup-ABCDEFG",
                             |            "groupId": "sg-abcdefg",
                             |            "description": "app instance",
                             |            "ipPermissions": [
                             |                {
                             |                    "ipProtocol": "tcp",
                             |                    "fromPort": 8888,
                             |                    "toPort": 8888,
                             |                    "userIdGroupPairs": [
                             |                        {
                             |                            "userId": "987654321",
                             |                            "groupName": null,
                             |                            "groupId": "sg-gfedcba",
                             |                            "vpcId": null,
                             |                            "vpcPeeringConnectionId": null,
                             |                            "peeringStatus": null
                             |                        }
                             |                    ],
                             |                    "ipRanges": [],
                             |                    "prefixListIds": []
                             |                },
                             |                {
                             |                    "ipProtocol": "tcp",
                             |                    "fromPort": 22,
                             |                    "toPort": 22,
                             |                    "userIdGroupPairs": [],
                             |                    "ipRanges": [
                             |                        "1.2.3.4/28"
                             |                    ],
                             |                    "prefixListIds": []
                             |                }
                             |            ],
                             |            "ipPermissionsEgress": [
                             |                {
                             |                    "ipProtocol": "tcp",
                             |                    "fromPort": 80,
                             |                    "toPort": 80,
                             |                    "userIdGroupPairs": [],
                             |                    "ipRanges": [
                             |                        "0.0.0.0/0"
                             |                    ],
                             |                    "prefixListIds": []
                             |                },
                             |                {
                             |                    "ipProtocol": "tcp",
                             |                    "fromPort": 443,
                             |                    "toPort": 443,
                             |                    "userIdGroupPairs": [],
                             |                    "ipRanges": [
                             |                        "0.0.0.0/0"
                             |                    ],
                             |                    "prefixListIds": []
                             |                }
                             |            ],
                             |            "vpcId": "vpc-0123456",
                             |            "tags": [
                             |                {
                             |                    "key": "Stack",
                             |                    "value": "stack"
                             |                },
                             |                {
                             |                    "key": "App",
                             |                    "value": "app"
                             |                },
                             |                {
                             |                    "key": "aws:cloudformation:stack-name",
                             |                    "value": "app"
                             |                },
                             |                {
                             |                    "key": "Stage",
                             |                    "value": "PROD"
                             |                },
                             |                {
                             |                    "key": "aws:cloudformation:stack-id",
                             |                    "value": "arn:aws:cloudformation:eu-west-1:$accountId:stack/app/cf-id"
                             |                },
                             |                {
                             |                    "key": "aws:cloudformation:logical-id",
                             |                    "value": "InstanceSecurityGroup"
                             |                }
                             |            ]
                             |        },
                             |        "supplementaryConfiguration": {},
                             |        "tags": {
                             |            "App": "app",
                             |            "aws:cloudformation:stack-name": "app",
                             |            "aws:cloudformation:stack-id": "arn:aws:cloudformation:eu-west-1:$accountId:stack/app/cf-id",
                             |            "Stage": "PROD",
                             |            "aws:cloudformation:logical-id": "InstanceSecurityGroup",
                             |            "Stack": "stack"
                             |        },
                             |        "configurationItemVersion": "1.2",
                             |        "configurationItemCaptureTime": "2016-11-23T16:00:00.000Z",
                             |        "configurationStateId": 123456789,
                             |        "awsAccountId": "$accountId",
                             |        "configurationItemStatus": "OK",
                             |        "resourceType": "AWS::EC2::SecurityGroup",
                             |        "resourceId": "sg-abcdefg",
                             |        "resourceName": null,
                             |        "ARN": "arn:aws:ec2:eu-west-1:$accountId:security-group/sg-abcdefg",
                             |        "awsRegion": "eu-west-1",
                             |        "availabilityZone": "Not Applicable",
                             |        "configurationStateMd5Hash": "b1946ac92492d2347c6235b4d2611184",
                             |        "resourceCreationTime": null
                             |    },
                             |    "notificationCreationTime": "2016-11-23T17:20:30.000Z",
                             |    "messageType": "ConfigurationItemChangeNotification",
                             |    "recordVersion": "1.2"
                             |}""".stripMargin

  val configEvent = new ConfigEvent()
  configEvent.setAccountId(accountId)
  configEvent.setConfigRuleArn(configRuleArn)
  configEvent.setConfigRuleId("config-rule-id")
  configEvent.setConfigRuleName("Config rule")
  configEvent.setEventLeftScope(false)
  configEvent.setExecutionRoleArn("role-arn")
  configEvent.setInvokingEvent(invokingEventJson)
  configEvent.setResultToken("abcdefg123456")
  configEvent.setRuleParameters("""{"key":"value"}""")
  configEvent.setVersion("1.0")
}
