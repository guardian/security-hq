package com.gu.hq.lambda.fixtures

import play.api.libs.json.Json

object SecurityGroups {
  import Common.accountId

  val sgConfigurationString =
    s"""{
       |    "ownerId": "$accountId",
       |    "groupName": "app-InstanceSecurityGroup-ABCDEFG",
       |    "groupId": "sg-abcdefg",
       |    "description": "description of security group",
       |    "ipPermissions": [
       |        {
       |            "ipProtocol": "tcp",
       |            "fromPort": 8888,
       |            "toPort": 8888,
       |            "userIdGroupPairs": [
       |                {
       |                    "userId": "987654321",
       |                    "groupName": null,
       |                    "groupId": "sg-gfedcba",
       |                    "vpcId": null,
       |                    "vpcPeeringConnectionId": null,
       |                    "peeringStatus": null
       |                }
       |            ],
       |            "ipRanges": [],
       |            "prefixListIds": []
       |        },
       |        {
       |            "ipProtocol": "tcp",
       |            "fromPort": 22,
       |            "toPort": 22,
       |            "userIdGroupPairs": [],
       |            "ipRanges": [
       |                "1.2.3.4/28"
       |            ],
       |            "prefixListIds": []
       |        }
       |    ],
       |    "ipPermissionsEgress": [
       |        {
       |            "ipProtocol": "tcp",
       |            "fromPort": 80,
       |            "toPort": 80,
       |            "userIdGroupPairs": [],
       |            "ipRanges": [
       |                "0.0.0.0/0"
       |            ],
       |            "prefixListIds": []
       |        },
       |        {
       |            "ipProtocol": "tcp",
       |            "fromPort": 443,
       |            "toPort": 443,
       |            "userIdGroupPairs": [],
       |            "ipRanges": [
       |                "0.0.0.0/0"
       |            ],
       |            "prefixListIds": []
       |        }
       |    ],
       |    "vpcId": "vpc-0123456",
       |    "tags": [
       |        {
       |            "key": "Stack",
       |            "value": "stack"
       |        },
       |        {
       |            "key": "App",
       |            "value": "app"
       |        },
       |        {
       |            "key": "aws:cloudformation:stack-name",
       |            "value": "app"
       |        },
       |        {
       |            "key": "Stage",
       |            "value": "PROD"
       |        },
       |        {
       |            "key": "aws:cloudformation:stack-id",
       |            "value": "arn:aws:cloudformation:eu-west-1:$accountId:stack/app/cf-id"
       |        },
       |        {
       |            "key": "aws:cloudformation:logical-id",
       |            "value": "InstanceSecurityGroup"
       |        }
       |    ]
       |}""".stripMargin
  val sgConfigurationJson = Json.parse(sgConfigurationString)
}
