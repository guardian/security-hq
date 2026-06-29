package aws

import model.AwsAccount
import software.amazon.awssdk.regions.Region

case class AwsClient[A](
  client: A,
  account: AwsAccount,
  region: Region
)