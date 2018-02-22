package aws.iam

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.{AmazonCloudFormationAsync, AmazonCloudFormationAsyncClient}
import com.amazonaws.services.cloudformation.model.{DescribeStacksRequest, DescribeStacksResult, Stack}
import model.AwsAccount
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object CloudFormation {
  private[iam] def client(account: AwsAccount, region: Region = Region.getRegion(Regions.EU_WEST_1)): AmazonCloudFormationAsync = {
    val auth = AWS.credentialsProvider(account)
    AmazonCloudFormationAsyncClient.asyncBuilder()
      .withCredentials(auth)
      .withRegion(Option(Regions.getCurrentRegion).getOrElse(region).getName).build()
  }

  private[iam] def getStackDescriptions(client: AmazonCloudFormationAsync)(implicit ec: ExecutionContext): Attempt[DescribeStacksResult] = {
    val request = new DescribeStacksRequest()
    handleAWSErrs(awsToScala(client.describeStacksAsync)(request))
  }

  private[iam] def getUserStacks(stacks: DescribeStacksResult): List[Stack] = {
    for {
      stack <- stacks.getStacks.asScala.toList
      output <- stack.getOutputs.asScala.toList
      if output.getOutputValue.contains(":user")
    } yield stack
  }
}
