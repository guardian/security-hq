package aws.s3

import aws.AwsClient
import com.amazonaws.services.s3.AmazonS3
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.io.BufferedSource
import scala.util.control.NonFatal

object S3 {
  def getS3Object(s3Client: AwsClient[AmazonS3], bucket: String, key: String): Attempt[BufferedSource] = {
    try {
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
