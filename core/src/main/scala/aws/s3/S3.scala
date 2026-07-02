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
  // Safely extract the AWS error code from an S3Exception. Both awsErrorDetails
  // and errorCode may be null, so this returns None rather than throwing.
  private def errorCodeOf(e: S3Exception): Option[String] =
    Option(e.awsErrorDetails).flatMap(details => Option(details.errorCode))

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
      // Match on the stable AWS error code rather than the exception message,
      // which is more robust across SDK versions.
      //
      // Both `awsErrorDetails` and `errorCode` can be null depending on how the
      // S3Exception was constructed or deserialised, so guard the access via Option
      // to avoid a NullPointerException. Malformed exceptions with no error code fall
      // through to the NonFatal handling below.
      case e: S3Exception if errorCodeOf(e).contains("ServerSideEncryptionConfigurationNotFoundError") =>
        // If there is no bucket encryption, AWS returns this error, so assume the bucket is not encrypted.
        Attempt.Right(NotEncrypted)
      case e: S3Exception if errorCodeOf(e).contains("NoSuchBucket") =>
        Attempt.Right(BucketNotFound)
      case e: S3Exception if errorCodeOf(e).contains("AccessDenied") =>
        // A restrictive bucket policy can explicitly deny GetEncryptionConfiguration.
        // Treat the encryption status as unknown so a single bucket does not fail the
        // whole account's report (and, in turn, block metrics for all accounts).
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
