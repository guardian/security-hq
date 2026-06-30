package aws.ec2

import aws.AwsAsyncHandler.{asScala, handleAWSErrs}
import aws.AwsClient
import utils.attempt.Attempt

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext

import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.model.DescribeRegionsRequest
import software.amazon.awssdk.services.ec2.model.Region

object EC2 {

  def getAvailableRegions(client: AwsClient[Ec2AsyncClient])(implicit ec: ExecutionContext): Attempt[List[Region]] = {
    val request = DescribeRegionsRequest.builder.build()
    handleAWSErrs(client)(asScala(client.client.describeRegions(request))).map { result =>
      result.regions.asScala.toList
    }
  }

}
