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

  def client(cfnClients: Map[(String, Regions), AmazonCloudFormationAsync], awsAccount: AwsAccount, region: Regions): Attempt[AmazonCloudFormationAsync] =
    Attempt.fromOption(cfnClients.get((awsAccount.id, region)), FailedAttempt(Failure(
      s"No AWS Cloudformation Client exists for ${awsAccount.id} and $region",
      s"Cannot find Cloudformation Client",
      500
    )
  ))
  private def getStackDescriptions(client: AmazonCloudFormationAsync, account: AwsAccount, region: Regions)(implicit ec: ExecutionContext): Attempt[List[Stack]] = {
    val request = new DescribeStacksRequest()
    handleAWSErrs(Some(account), Some(region))(awsToScala(Some(account), Some(region))(client.describeStacksAsync)(request)).map(_.getStacks.asScala.toList)
  }

  private def getStacks(account: AwsAccount, region: Regions, cfnClients: Map[(String, Regions), AmazonCloudFormationAsync])(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      cloudClient <- client(cfnClients, account, region)
      stacks <- getStackDescriptions(cloudClient, account, region)
    } yield parseStacks(stacks, region)
  }

  def getStacksFromAllRegions(
    account: AwsAccount,
    cfnClients: Map[(String, Regions), AmazonCloudFormationAsync],
    ec2Clients: Map[(String, Regions), AmazonEC2Async]
  )(implicit ec: ExecutionContext): Attempt[List[AwsStack]] = {
    for {
      stacks <- Attempt.flatTraverse(Regions.values().toList)(region => getStacks(account, region, cfnClients))
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
