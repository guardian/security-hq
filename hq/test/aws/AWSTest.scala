package aws

import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigFactory
import org.scalatest.prop.{Checkers, PropertyChecks}
import org.scalatest.{FreeSpec, Matchers}
import play.api.Configuration
import utils.attempt.AttemptValues

class AWSTest extends FreeSpec with Matchers with Checkers with PropertyChecks with AttemptValues {

  "clients" - {

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
    
    //Two accounts, all regions.
    val allRegionsSize = Regions.values().size * 2
    // Only in one region.
    val singleRegionSize = 2

    "inspector" in {
      AWS.inspectorClients(configuration) should have size(singleRegionSize)
    }
    "ec2" in {
      AWS.ec2Clients(configuration) should have size(allRegionsSize)
    }

    "cloudformation" in {
      AWS.cfnClients(configuration) should have size(allRegionsSize)
    }

    "trusted advisor" in {
      AWS.taClients(configuration) should have size(singleRegionSize)
    }

    "iam" in {
      AWS.iamClients(configuration) should have size(allRegionsSize)
    }

  }
}
