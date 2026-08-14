package aws.s3

import model.{BucketEncryptionResponse, BucketNotFound, Encrypted, NotEncrypted}
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetBucketEncryptionRequest,
  GetObjectRequest,
  GetObjectResponse,
  S3Exception
}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.util.control.NonFatal

object S3 {
  def getS3Object(s3Client: S3Client, bucket: String, key: String): Attempt[ResponseBytes[GetObjectResponse]] = {
    val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
    try {
      Attempt.Right {
        s3Client.getObject(request, ResponseTransformer.toBytes())
      }
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          FailedAttempt(
            Failure(
              s"Unable to get S3 object for bucket $bucket and key $key",
              throwable = Some(e)
            )
          )
        )
    }
  }

  def getBucketEncryption(client: S3Client, bucketName: String): Attempt[BucketEncryptionResponse] = {
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
      case e: S3Exception
          if Option(e.getMessage).exists(_.contains("ServerSideEncryptionConfigurationNotFoundError")) =>
        Attempt.Right(NotEncrypted)
      case e: S3Exception if Option(e.getMessage).exists(_.contains("NoSuchBucket")) =>
        Attempt.Right(BucketNotFound)
      case NonFatal(e) =>
        Attempt.Left(
          FailedAttempt(
            Failure(
              s"unable to get S3 bucket encryption status for bucket $bucketName",
              throwable = Some(e)
            )
          )
        )
    }
  }
}
