package aws.s3

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{AmazonS3Exception, GetBucketEncryptionResult}
import model.{BucketEncryptionResponse, BucketNotFound, Encrypted, NotEncrypted}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.io.BufferedSource
import scala.util.control.NonFatal

object S3 {
  def getS3Object(s3Client: AmazonS3, bucket: String, key: String): Attempt[BufferedSource] = {
    try {
      Attempt.Right {
        scala.io.Source
          .fromInputStream(s3Client.getObject(bucket, key).getObjectContent)
      }
    } catch {
      case NonFatal(e) =>
        Attempt.Left(FailedAttempt(Failure(
          s"Unable to get S3 object for bucket $bucket and key $key",
          "Failed to fetch an S3 object",
          500,
          throwable = Some(e)
        )))
    }
  }

  def getBucketEncryption(client: AmazonS3, bucketName: String)(implicit ec: ExecutionContext): Attempt[BucketEncryptionResponse] = {
    try {
      Attempt.Right {
        Option(
          client.getBucketEncryption(bucketName).getServerSideEncryptionConfiguration
        ).fold[BucketEncryptionResponse](NotEncrypted)(_ => Encrypted)
      }
    } catch {
      // If there is no bucket encryption, AWS returns an error...
      // Assume bucket is not encrypted if we receive the specific error
      case e: AmazonS3Exception if e.getMessage.contains("ServerSideEncryptionConfigurationNotFoundError") =>
        Attempt.Right(NotEncrypted)
      case e: AmazonS3Exception if e.getMessage.contains("NoSuchBucket") =>
        Attempt.Right(BucketNotFound)
      case NonFatal(e) =>
        Attempt.Left(FailedAttempt(Failure(
          s"unable to get S3 bucket encryption status for bucket $bucketName",
          "Encryption status for this bucket was not found.",
          500,
          context = Some(e.getMessage),
          throwable = Some(e)
        )))
    }
  }
}
