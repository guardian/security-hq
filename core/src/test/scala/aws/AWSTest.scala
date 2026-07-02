package aws

import model.AwsAccount
import org.scalatest.freespec.AnyFreeSpec
import utils.attempt.AttemptValues
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.regions.Region

class AWSTest extends AnyFreeSpec with Matchers with AttemptValues {

  "real clients" - {

    val accounts = List(
      AwsAccount("test1", "Test1", "", ""),
      AwsAccount("test2", "Test2", "", "")
    )

    val regions =
      List(Region.of("eu-west-1"), Region.of("eu-west-2"), Region.of("eu-west-3"), Region.of("eu-central-1"))

    // Two accounts, three regions.
    val allRegionsSize = regions.size * 2
    // Only in one region.
    val singleRegionSize = 2

    "ec2" in {
      AWS.ec2Clients(accounts, regions) should have size (allRegionsSize)
    }

    "cloudformation" in {
      AWS.cfnClients(accounts, regions) should have size (allRegionsSize)
    }

    "trusted advisor" in {
      AWS.taClients(accounts) should have size (singleRegionSize)
    }

    "iam" in {
      AWS.iamClients(accounts, regions) should have size (allRegionsSize)
    }

  }

}
