package com.gu.hq.lambda.fixtures

object Diffs {
  val createIpDiffJson =
    """{
      |    "changedProperties": {
      |        "Configuration.IpPermissions.0": {
      |            "previousValue": null,
      |            "updatedValue": {
      |                "ipProtocol": "tcp",
      |                "fromPort": 443,
      |                "toPort": 443,
      |                "userIdGroupPairs": [],
      |                "ipRanges": [
      |                    "1.2.3.4/32"
      |                ],
      |                "prefixListIds": []
      |            },
      |            "changeType": "CREATE"
      |        }
      |    },
      |    "changeType": "UPDATE"
      |}
    """.stripMargin
  val deleteIpDiff =
    """{
      |    "changedProperties": {
      |        "Configuration.IpPermissions.0": {
      |            "previousValue": {
      |                "ipProtocol": "tcp",
      |                "fromPort": 443,
      |                "toPort": 443,
      |                "userIdGroupPairs": [],
      |                "ipRanges": [
      |                    "1.2.3.4/32"
      |                ],
      |                "prefixListIds": []
      |            },
      |            "updatedValue": null,
      |            "changeType": "DELETE"
      |        }
      |    },
      |    "changeType": "UPDATE"
      |}
    """.stripMargin
}
