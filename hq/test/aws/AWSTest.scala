package aws

import com.amazonaws.regions.{RegionUtils, Regions}
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpec
import play.api.Configuration
import utils.attempt.AttemptValues
import org.scalatest.matchers.should.Matchers

class AWSTest extends AnyFreeSpec with Matchers with AttemptValues {

  "real clients" - {

    val config = ConfigFactory.parseString(
      s"""
         | {
         |   "hq": {
         |     "accounts" : [
         |       {
         |         "name": "Test1"
         |         "id": "test1"
         |         "roleArn": ""
         |         "number": ""
         |       },
         |       {
         |         "name": "Test2"
         |         "id": "test2"
         |         "roleArn": ""
         |         "number": ""
         |       }
         |     ]
         |   }
         | }
       """.stripMargin
    )
    val configuration = Configuration(config)

    val regions = List(RegionUtils.getRegion("eu-west-1"), RegionUtils.getRegion("eu-west-2"), RegionUtils.getRegion("eu-west-3"), RegionUtils.getRegion("eu-central-1"))

    //Two accounts, three regions.
    val allRegionsSize = regions.size * 2
    // Only in one region.
    val singleRegionSize = 2

    "ec2" in {
      AWS.ec2Clients(configuration, regions) should have size(allRegionsSize)
    }

    "cloudformation" in {
      AWS.cfnClients(configuration, regions) should have size(allRegionsSize)
    }

    "trusted advisor" in {
      AWS.taClients(configuration) should have size(singleRegionSize)
    }

    "iam" in {
      AWS.iamClients(configuration, regions) should have size(allRegionsSize)
    }

  }

  "mock clients" - {

    val config = ConfigFactory.parseString(
      s"""
         | {
         |   "hq": {
         |     "accounts" : [
         |       {
         |         "name": "Mock"
         |         "id": "mock"
         |         "roleArn": ""
         |         "number": ""
         |       }
         |     ]
         |   }
         | }
       """.stripMargin
    )
    val configuration = Configuration(config)

  }

}
