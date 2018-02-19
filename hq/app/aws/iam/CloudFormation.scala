package aws.iam

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.ec2.EC2
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.model.{DescribeStackResourcesRequest, DescribeStackResourcesResult, DescribeStacksRequest, DescribeStacksResult}
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder}
import model.{AwsAccount, Stack, StackResource}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object CloudFormation {
  private def client(auth: AWSCredentialsProviderChain, region: Region = Region.getRegion(Regions.EU_WEST_1)): AmazonCloudFormationAsync = {
    AmazonCloudFormationAsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(region.getName)
      .build()
  }
  private def client(awsAccount: AwsAccount, region: Region): AmazonCloudFormationAsync = {
    val auth = AWS.credentialsProvider(awsAccount)
    client(auth, region)
  }

  private def getStackDescriptions(client: AmazonCloudFormationAsync)(implicit ec: ExecutionContext): Attempt[List[Stack]] = {
    val request = new DescribeStacksRequest()
    handleAWSErrs(awsToScala(client.describeStacksAsync)(request)).map(parseStacksResult)
  }

  private def getStackResources(stackName: String)(client: AmazonCloudFormationAsync)(implicit ec: ExecutionContext): Attempt[List[StackResource]] = {
    val request = new DescribeStackResourcesRequest().withStackName(stackName)
    handleAWSErrs(awsToScala(client.describeStackResourcesAsync)(request)).map(parseResourcesResult)
  }

  private[iam] def getStacksFromAllRegions(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[Stack]] = {
    val regionClient = EC2.client(account)
    for {
      availableRegions <- EC2.getAvailableRegions(regionClient)
      regions = availableRegions.map(region => Region.getRegion(Regions.fromName(region.getRegionName)))
      clients = regions.map(region => CloudFormation.client(account, region))
      results <- Attempt.flatTraverse(clients)(getStackDescriptions)
    } yield results
  }

  private def parseResourcesResult(result: DescribeStackResourcesResult): List[StackResource] = {
    for {
      resource <- result.getStackResources.asScala.toList
    } yield StackResource(
      resource.getStackId,
      resource.getStackName,
      resource.getPhysicalResourceId,
      resource.getLogicalResourceId,
      resource.getResourceStatus,
      resource.getResourceType
    )
  }

  private[iam] def parseStacksResult(result: DescribeStacksResult): List[Stack] = {
    for {
      stack <- result.getStacks.asScala.toList
      output <- stack.getOutputs.asScala.toList
    } yield Stack(
      stack.getStackId,
      stack.getStackName,
      output.getOutputValue,
      None
    )
  }
}
