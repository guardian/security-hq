package aws.cloudformation

import aws.{AwsClient, AwsClients}
import aws.AwsAsyncHandler.{asScala, handleAWSErrs}
import model.{AwsAccount, AwsStack}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.cloudformation.model.{Stack, DescribeStacksRequest}

object CloudFormation {

  private def getStackDescriptions(client: AwsClient[CloudFormationAsyncClient], account: AwsAccount, region: Region)(implicit ec: ExecutionContext): Attempt[List[Stack]] = {
    val request = DescribeStacksRequest.builder.build()
    handleAWSErrs(client)(asScala(client.client.describeStacks(request))).map(_.stacks.asScala.toList)
  }

  private def getStacks(account: AwsAccount, region: Region, cfnClients: AwsClients[CloudFormationAsyncClient])(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      cloudClient <- cfnClients.get(account, region)
      stacks <- getStackDescriptions(cloudClient, account, region)
    } yield parseStacks(stacks, region)
  }

  def getStacksFromAllRegions(
    account: AwsAccount,
    cfnClients: AwsClients[CloudFormationAsyncClient],
    regions: List[Region]
  )(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      stacks <- Attempt.flatTraverse(regions)(region => getStacks(account, region, cfnClients))
    } yield stacks
  }

  private[cloudformation] def parseStacks(stacks: List[Stack], region: Region): List[AwsStack] = {
    stacks.map { stack =>
      AwsStack(
        stack.stackId,
        stack.stackName,
        region.id
      )
    }
  }
}
