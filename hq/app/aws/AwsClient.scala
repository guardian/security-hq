package aws

import com.amazonaws.regions.Regions
import model.AwsAccount

case class AwsClient[A](
  client: A,
  account: AwsAccount,
  region: Regions
)