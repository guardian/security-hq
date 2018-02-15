package aws.iam

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.ec2.EC2
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.model.{DescribeStacksRequest, DescribeStacksResult}
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder}
import model.{AwsAccount, Stack}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object CloudFormation {
  private def cloudClient(auth: AWSCredentialsProviderChain, region: Region = Region.getRegion(Regions.EU_WEST_1)): AmazonCloudFormationAsync = {
    AmazonCloudFormationAsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(region.getName)
      .build()
  }
  private[iam] def client(awsAccount: AwsAccount, region: Region): AmazonCloudFormationAsync = {
    val auth = AWS.credentialsProvider(awsAccount)
    cloudClient(auth, region)
  }

  private[iam] def getStackDescriptions(client: AmazonCloudFormationAsync)(implicit ec: ExecutionContext): Attempt[DescribeStacksResult] = {
    val request = new DescribeStacksRequest()
    handleAWSErrs(awsToScala(client.describeStacksAsync)(request))
  }

  private[iam] def getStacksFromAllRegions(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[DescribeStacksResult]] = {
    val regionClient = EC2.client(account)
    for {
      regions <- EC2.getAvailableRegions(regionClient)
      clients = regions.map(region => CloudFormation.client(account, Region.getRegion(Regions.fromName(region.getRegionName))))
      describeStacksResult <- Attempt.traverse(clients)(getStackDescriptions)
    } yield describeStacksResult
  }

  private[iam] def extractUserStacks(stacksResults: List[DescribeStacksResult]): List[Stack] = {
    for {
      result <- stacksResults
      stack <- result.getStacks.asScala.toList
      output <- stack.getOutputs.asScala.toList
      if output.getOutputValue.contains(":user")
    } yield Stack(stack.getStackId, stack.getStackName, output.getOutputValue)
  }
}
