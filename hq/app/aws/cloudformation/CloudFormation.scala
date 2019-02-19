package aws.cloudformation

import aws.{AwsClient, AwsClients}
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync
import com.amazonaws.services.cloudformation.model._
import model.{AwsAccount, AwsStack}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object CloudFormation {

  private def getStackDescriptions(client: AwsClient[AmazonCloudFormationAsync], account: AwsAccount, region: Regions)(implicit ec: ExecutionContext): Attempt[List[Stack]] = {
    val request = new DescribeStacksRequest()
    handleAWSErrs(client)(awsToScala(client)(_.describeStacksAsync)(request)).map(_.getStacks.asScala.toList)
  }

  private def getStacks(account: AwsAccount, region: Regions, cfnClients: AwsClients[AmazonCloudFormationAsync])(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      cloudClient <- cfnClients.get(account, region)
      stacks <- getStackDescriptions(cloudClient, account, region)
    } yield parseStacks(stacks, region)
  }

  def getStacksFromAllRegions(
    account: AwsAccount,
    cfnClients: AwsClients[AmazonCloudFormationAsync],
    regions: List[Regions]
  )(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      stacks <- Attempt.flatTraverse(regions)(region => getStacks(account, region, cfnClients))
    } yield stacks
  }

  private[cloudformation] def parseStacks(stacks: List[Stack], region: Regions): List[AwsStack] = {
    stacks.map { stack =>
      AwsStack(
        stack.getStackId,
        stack.getStackName,
        region.getName
      )
    }
  }
}
