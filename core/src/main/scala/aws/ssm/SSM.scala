package aws.ssm

import aws.AwsClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmAsyncClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import scala.concurrent.ExecutionContext
import utils.attempt.Attempt
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest
import aws.AwsAsyncHandler.handleAWSErrs
import aws.AwsAsyncHandler.asScala

import scala.jdk.CollectionConverters.*
import software.amazon.awssdk.services.s3.S3Client
import config.CoreConfig
import software.amazon.awssdk.services.sts.StsClient

object SSM {

  def getAllRegions(client: AwsClient[SsmAsyncClient])(using ExecutionContext): Attempt[List[Region]] = {

    def paginate(found: List[Region], nextToken: Option[String]): Attempt[List[Region]] = {
      val request = GetParametersByPathRequest
        .builder()
        .path("/aws/service/global-infrastructure/regions")
        .nextToken(nextToken.orNull)
        .build()

      handleAWSErrs(client)(asScala(client.client.getParametersByPath(request))).flatMap { response =>
        val regions = found ++ response.parameters().asScala.map(p => Region.of(p.value()))
        Option(response.nextToken()) match {
          case Some(value) => paginate(regions, Some(value))
          case None        => Attempt.Right(regions)
        }
      }
    }

    paginate(Nil, None)
  }

}
