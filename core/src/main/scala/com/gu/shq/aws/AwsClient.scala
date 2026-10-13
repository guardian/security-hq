package com.gu.shq.aws

import com.gu.shq.model.AwsAccount
import software.amazon.awssdk.regions.Region

case class AwsClient[A](
    client: A,
    account: AwsAccount,
    region: Region
)
