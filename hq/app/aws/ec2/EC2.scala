package aws.ec2

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.AwsClient
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._
import utils.attempt.Attempt

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext


object EC2 {

  def getAvailableRegions(client: AwsClient[AmazonEC2Async])(implicit ec: ExecutionContext): Attempt[List[Region]] = {
    val request = new DescribeRegionsRequest()
    handleAWSErrs(client)(awsToScala(client)(_.describeRegionsAsync)(request)).map { result =>
      result.getRegions.asScala.toList
    }
  }

}
