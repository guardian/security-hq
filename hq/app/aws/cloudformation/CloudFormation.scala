package aws.cloudformation

import aws.AwsAsyncHandler.{asScala, handleAWSErrs}
import aws.{AwsClient, AwsClients, AwsClientsList}
import model.{AwsAccount, AwsStack}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.cloudformation.model.{DescribeStacksRequest, Stack}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

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
