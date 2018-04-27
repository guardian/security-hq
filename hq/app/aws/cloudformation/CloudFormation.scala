package aws.cloudformation

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.ec2.EC2
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync
import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.ec2.AmazonEC2Async
import model.{AwsAccount, AwsStack}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object CloudFormation {

  def client(cfnClients: Map[(String, Regions),AmazonCloudFormationAsync], awsAccount: AwsAccount, region: Regions): Attempt[AmazonCloudFormationAsync] =
    Attempt.fromOption(cfnClients.get((awsAccount.id, region)), FailedAttempt(Failure(
      s"No AWS Cloudformation Client exists for ${awsAccount.id} and $region",
      s"Cannot find Cloudformation Client",
      500
    )
  ))
  private def getStackDescriptions(client: AmazonCloudFormationAsync)(implicit ec: ExecutionContext): Attempt[List[Stack]] = {
    val request = new DescribeStacksRequest()
    handleAWSErrs(awsToScala(client.describeStacksAsync)(request)).map(_.getStacks.asScala.toList)
  }

  private def getStacks(account: AwsAccount, region: Regions, cfnClients: Map[(String, Regions),AmazonCloudFormationAsync])(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      cloudClient <- client(cfnClients, account, region)
      stacks <- getStackDescriptions(cloudClient)
    } yield parseStacks(stacks, region)
  }

  def getStacksFromAllRegions(
    account: AwsAccount,
    cfnClients: Map[(String, Regions), AmazonCloudFormationAsync],
    ec2Clients: Map[(String, Regions), AmazonEC2Async]
  )(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      regionClient <- EC2.client(ec2Clients, account, Regions.EU_WEST_1)
      availableRegions <- EC2.getAvailableRegions(regionClient)
      regions = availableRegions.map(region => Regions.fromName(region.getRegionName))
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
