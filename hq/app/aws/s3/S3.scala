package aws.s3

import aws.AwsClient
import com.amazonaws.services.s3.AmazonS3
import play.api.Logging
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.io.BufferedSource
import scala.util.control.NonFatal

object S3 extends Logging {
  def getS3Object(s3Client: AwsClient[AmazonS3], bucket: String, key: String): Attempt[BufferedSource] = {
    try {
      logger.info(s"Making get-object request to S3 for bucket $bucket and key $key using region ${s3Client.region} and account ${s3Client.account}")
      Attempt.Right {
        scala.io.Source
          .fromInputStream(s3Client.client.getObject(bucket, key).getObjectContent)
      }
    } catch {
      case NonFatal(e) =>
        Attempt.Left(FailedAttempt(Failure(
          "unable to get S3 object for the unrecognised user job",
          "I haven't been able to get the S3 object for the unrecognised user job, which contains the Janus data",
          500,
          context = Some(e.getMessage),
          throwable = Some(e)
        )))
    }
  }
}
