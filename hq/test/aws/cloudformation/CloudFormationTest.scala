package aws.cloudformation

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.model.Stack
import model.AwsStack
import org.scalatest.{FreeSpec, Matchers}

class CloudFormationTest extends FreeSpec with Matchers {

  "parseStacks" - {
    val stackId = "arn:aws:cloudformation:eu-west-1:123456789123:stack/stack-name/8a123bc0-222d-33e4-5fg6-77aa88b12345"
    val region = Region.getRegion(Regions.EU_WEST_1)
    val exampleStack = new Stack().withStackId(stackId).withStackName("example-stack-A")

    "will parse and extract each stack, including setting the the region" in {
      val result = CloudFormation.parseStacks(List(exampleStack), region)
      result shouldEqual List(AwsStack(stackId, "example-stack-A", "eu-west-1"))
    }

    "returns an empty list if there are no stacks" in {
      CloudFormation.parseStacks(List.empty, region) shouldEqual List.empty
    }
  }
}
