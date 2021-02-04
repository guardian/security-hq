package aws

import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigFactory
import org.scalatest.prop.{Checkers, PropertyChecks}
import org.scalatest.{FreeSpec, Matchers}
import play.api.Configuration
import utils.attempt.AttemptValues

class AWSTest extends FreeSpec with Matchers with Checkers with PropertyChecks with AttemptValues {

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
         |       },
         |       {
         |         "name": "Test2"
         |         "id": "test2"
         |         "roleArn": ""
         |       }
         |     ]
         |   }
         | }
       """.stripMargin
    )
    val configuration = Configuration(config)

    val regions = List(Regions.EU_WEST_1, Regions.EU_WEST_2, Regions.EU_WEST_3, Regions.EU_CENTRAL_1)

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
         |       }
         |     ]
         |   }
         | }
       """.stripMargin
    )
    val configuration = Configuration(config)

  }

}
