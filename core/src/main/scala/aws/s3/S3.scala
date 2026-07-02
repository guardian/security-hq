package aws.s3

import model.{BucketEncryptionResponse, BucketNotFound, Encrypted, EncryptionUnknown, NotEncrypted}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.io.BufferedSource
import scala.util.control.NonFatal

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.{GetBucketEncryptionRequest, GetObjectRequest}

object S3 {

  /** Extracts the AWS error code from an [[S3Exception]], if present.
    *
    * `awsErrorDetails` may be null when the exception carries no service-provided error details, so this is guarded
    * explicitly to avoid a `NullPointerException` when matching on the error code.
    */
  private def errorCode(e: S3Exception): Option[String] =
    Option(e.awsErrorDetails()).flatMap(details => Option(details.errorCode()))

  def getS3Object(s3Client: S3Client, bucket: String, key: String): Attempt[BufferedSource] = {
    val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
    try {
      Attempt.Right {
        scala.io.Source
          .fromInputStream(s3Client.getObject(request))
      }
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          FailedAttempt(
            Failure(
              s"Unable to get S3 object for bucket $bucket and key $key",
              "Failed to fetch an S3 object",
              500,
              throwable = Some(e)
            )
          )
        )
    }
  }

  def getBucketEncryption(client: S3Client, bucketName: String)(implicit
      ec: ExecutionContext
  ): Attempt[BucketEncryptionResponse] = {
    val request = GetBucketEncryptionRequest.builder().bucket(bucketName).build()
    try {
      Attempt.Right {
        Option(
          client.getBucketEncryption(request).serverSideEncryptionConfiguration
        ).fold[BucketEncryptionResponse](NotEncrypted)(_ => Encrypted)
      }
    } catch {
      // If there is no bucket encryption, AWS returns an error...
      // Assume bucket is not encrypted if we receive the specific error code
      case e: S3Exception if errorCode(e).contains("ServerSideEncryptionConfigurationNotFoundError") =>
        Attempt.Right(NotEncrypted)
      case e: S3Exception if errorCode(e).contains("NoSuchBucket") =>
        Attempt.Right(BucketNotFound)
      case e: S3Exception if errorCode(e).contains("AccessDenied") =>
        // A restrictive bucket policy can explicitly deny GetEncryptionConfiguration.
        // Treat the encryption status as unknown so a single bucket does not fail the
        // whole account's report (and block metrics for all accounts).
        Attempt.Right(EncryptionUnknown)
      case NonFatal(e) =>
        Attempt.Left(
          FailedAttempt(
            Failure(
              s"unable to get S3 bucket encryption status for bucket $bucketName",
              "Encryption status for this bucket could not be retrieved due to an unexpected error.",
              500,
              context = Some(e.getMessage),
              throwable = Some(e)
            )
          )
        )
    }
  }
}
