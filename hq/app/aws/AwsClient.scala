package aws

import com.amazonaws.regions.Region
import model.AwsAccount

case class AwsClient[A](
  client: A,
  account: AwsAccount,
  region: Region
)