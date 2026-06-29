package aws.cloudformation

import model.AwsStack
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.model.{Stack, DescribeStacksRequest}


class CloudFormationTest extends AnyFreeSpec with Matchers {

  "parseStacks" - {
    val stackId = "arn:aws:cloudformation:eu-west-1:123456789123:stack/stack-name/8a123bc0-222d-33e4-5fg6-77aa88b12345"
    val region = Region.of("eu-west-1")
    val exampleStack = Stack.builder.stackId(stackId).stackName("example-stack-A").build()

    "will parse and extract each stack, including setting the the region" in {
      val result = CloudFormation.parseStacks(List(exampleStack), region)
      result shouldEqual List(AwsStack(stackId, "example-stack-A", "eu-west-1"))
    }

    "returns an empty list if there are no stacks" in {
      CloudFormation.parseStacks(List.empty, region) shouldEqual List.empty
    }
  }
}
