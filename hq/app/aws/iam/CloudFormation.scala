package aws.iam

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder}
import com.amazonaws.services.cloudformation.model.{DescribeStacksRequest, DescribeStacksResult, Stack}
import com.amazonaws.services.ec2.model.{DescribeRegionsRequest, DescribeRegionsResult}
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import model.AwsAccount
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

  def describeRegionsClient(account: AwsAccount, region: Region = Region.getRegion(Regions.EU_WEST_1)): AmazonEC2Async = {
    val auth = AWS.credentialsProvider(account)
    AmazonEC2AsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(Option(Regions.getCurrentRegion).getOrElse(region).getName)
      .build()
  }

  def describeRegions(client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[DescribeRegionsResult] = {
    val request = new DescribeRegionsRequest()
    handleAWSErrs(awsToScala(client.describeRegionsAsync)(request))
  }

  private[iam] def getStackDescriptions(client: AmazonCloudFormationAsync)(implicit ec: ExecutionContext): Attempt[DescribeStacksResult] = {
    val request = new DescribeStacksRequest()
    handleAWSErrs(awsToScala(client.describeStacksAsync)(request))
  }

  private[iam] def getStacksFromAllRegions(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[DescribeStacksResult]] = {
    val regionClient = describeRegionsClient(account)
    for {
      describeRegionsResult <- describeRegions(regionClient)
      regions = describeRegionsResult.getRegions.asScala.toList
      clients = regions.map(region => CloudFormation.client(account, Region.getRegion(Regions.fromName(region.getRegionName))))
      describeStacksResult <- Attempt.traverse(clients)(getStackDescriptions)
    } yield describeStacksResult
  }

  private[iam] def getUserStacks(stacksResults: List[DescribeStacksResult]): List[Stack] = {
    for {
      result <- stacksResults
      stack <- result.getStacks.asScala.toList
      output <- stack.getOutputs.asScala.toList
      if output.getOutputValue.contains(":user")
    } yield stack
  }
}
