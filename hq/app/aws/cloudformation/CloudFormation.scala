package aws.cloudformation

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.ec2.EC2
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClientBuilder}
import model.{AwsAccount, AwsStack}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object CloudFormation {
  private def client(auth: AWSCredentialsProviderChain, region: Region): AmazonCloudFormationAsync = {
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
    handleAWSErrs(awsToScala(client.describeStacksAsync)(request)).map(_.getStacks.asScala.toList)
  }

  private def getStacks(account: AwsAccount, region: Region)(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    val cloudClient = CloudFormation.client(account, region)
    for {
      stacks <- getStackDescriptions(cloudClient)
    } yield parseStacks(stacks, region)
  }

  def getStacksFromAllRegions(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    val regionClient = EC2.client(account)
    for {
      availableRegions <- EC2.getAvailableRegions(regionClient)
      regions = availableRegions.map(region => Region.getRegion(Regions.fromName(region.getRegionName)))
      stacks <- Attempt.flatTraverse(regions)(region => getStacks(account, region))
    } yield stacks
  }

  private[cloudformation] def parseStacks(stacks: List[Stack], region: Region): List[AwsStack] = {
    stacks.map { stack =>
      AwsStack(
        stack.getStackId,
        stack.getStackName,
        region.getName
      )
    }
  }
}
