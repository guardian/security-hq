package aws.s3

import model.{BucketEncryptionResponse, BucketNotFound, Encrypted, NotEncrypted}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.io.BufferedSource
import scala.util.control.NonFatal

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetBucketEncryptionRequest}

object S3 {
  def getS3Object(s3Client: S3Client, bucket: String, key: String): Attempt[BufferedSource] = {
    val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
    try {
      Attempt.Right {
        scala.io.Source
          .fromInputStream(s3Client.getObject(request))
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

  def getBucketEncryption(client: S3Client, bucketName: String)(implicit ec: ExecutionContext): Attempt[BucketEncryptionResponse] = {
    val request = GetBucketEncryptionRequest.builder().bucket(bucketName).build()
    try {
      Attempt.Right {
        Option(
          client.getBucketEncryption(request).serverSideEncryptionConfiguration
        ).fold[BucketEncryptionResponse](NotEncrypted)(_ => Encrypted)
      }
    } catch {
      // If there is no bucket encryption, AWS returns an error...
      // Assume bucket is not encrypted if we receive the specific error
      case e: S3Exception if e.getMessage.contains("ServerSideEncryptionConfigurationNotFoundError") =>
        Attempt.Right(NotEncrypted)
      case e: S3Exception if e.getMessage.contains("NoSuchBucket") =>
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
