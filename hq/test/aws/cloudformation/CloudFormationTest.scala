package aws.cloudformation

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.model.{DescribeStackResourcesResult, Stack, StackResource => Resource}
import model.{AwsStack, StackResource}
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConverters._

class CloudFormationTest extends FreeSpec with Matchers {

  "parseResourcesResult" - {
    val stackId = "arn:aws:cloudformation:eu-west-1:123456789123:stack/stack-name/8a123bc0-222d-33e4-5fg6-77aa88b12345"

    "will correctly parse and extract the resources" in {
      val keyStackResource = new Resource()
        .withStackId(stackId)
        .withStackName("example-key")
        .withLogicalResourceId("example")
        .withPhysicalResourceId("example-key-1ABC2D345EFG")
        .withResourceType("AWS::IAM::AccessKey")
        .withResourceStatus("CREATE_COMPLETE")
      val userStackResource = new Resource()
        .withStackId(stackId)
        .withStackName("example-user")
        .withLogicalResourceId("example")
        .withPhysicalResourceId("example-user-1ABC2D345EFG")
        .withResourceType("AWS::IAM::User")
        .withResourceStatus("CREATE_COMPLETE")

      val stackResourcesResult = new DescribeStackResourcesResult()
        .withStackResources(List(keyStackResource, userStackResource).asJavaCollection)

      CloudFormation.parseResourcesResult(stackResourcesResult) shouldEqual List(
        StackResource(stackId, "example-key", "example-key-1ABC2D345EFG", "example", "CREATE_COMPLETE", "AWS::IAM::AccessKey"),
        StackResource(stackId, "example-user", "example-user-1ABC2D345EFG", "example", "CREATE_COMPLETE", "AWS::IAM::User")
      )
    }

    "will return an empty list if there are no resources" in {
      val stackResourcesResult = new DescribeStackResourcesResult()
      CloudFormation.parseResourcesResult(stackResourcesResult) shouldEqual List.empty
    }
  }

  "parseStacksAndResources" - {
    val stackId = "arn:aws:cloudformation:eu-west-1:123456789123:stack/stack-name/8a123bc0-222d-33e4-5fg6-77aa88b12345"
    val stackResourceA = StackResource(stackId, "example-key", "example-key-1ABC2D345EFG", "example", "CREATE_COMPLETE", "AWS::IAM::AccessKey")
    val stackResourceB = StackResource(stackId, "example-user", "example-user-1ABC2D345EFG", "example", "CREATE_COMPLETE", "AWS::IAM::User")

    val region = Region.getRegion(Regions.EU_WEST_1)
    val exampleStack = new Stack().withStackId(stackId).withStackName("example-stack-A")

    "will parse and extract each stack, including setting the the region" in {
      val result = CloudFormation.parseStacksAndResources(List((exampleStack, List.empty)), region)
      result shouldEqual List(AwsStack(stackId, "example-stack-A", List.empty, "eu-west-1"))
    }

    "will add any associated stack resources to the generated stack" in {
      val result = CloudFormation.parseStacksAndResources(List((exampleStack, List(stackResourceA, stackResourceB))), region)
      result shouldEqual List(AwsStack(stackId, "example-stack-A", List(stackResourceA, stackResourceB), "eu-west-1"))
    }

    "returns an empty list if there are no stacks" in {
      CloudFormation.parseStacksAndResources(List.empty, region) shouldEqual List.empty
    }
  }

}
