package aws.s3

import model.{BucketNotFound, Encrypted, EncryptionUnknown, NotEncrypted}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetBucketEncryptionRequest,
  GetBucketEncryptionResponse,
  S3Exception,
  ServerSideEncryptionByDefault,
  ServerSideEncryptionConfiguration,
  ServerSideEncryptionRule
}
import utils.attempt.AttemptValues

import scala.concurrent.ExecutionContext.Implicits.global

class S3Test extends AnyFreeSpec with Matchers with AttemptValues {

  /** A minimal S3Client that only implements getBucketEncryption. All other operations inherit the SDK default
    * behaviour (throwing), which is fine as the code under test only calls this one method.
    */
  private def stubClient(onGetEncryption: GetBucketEncryptionRequest => GetBucketEncryptionResponse): S3Client =
    new S3Client {
      override def serviceName(): String = "s3-stub"
      override def close(): Unit = ()
      override def getBucketEncryption(request: GetBucketEncryptionRequest): GetBucketEncryptionResponse =
        onGetEncryption(request)
    }

  private def s3ExceptionWithCode(errorCode: String): S3Exception =
    S3Exception
      .builder()
      .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build())
      .build()
      .asInstanceOf[S3Exception]

  private val encryptedResponse =
    GetBucketEncryptionResponse
      .builder()
      .serverSideEncryptionConfiguration(
        ServerSideEncryptionConfiguration
          .builder()
          .rules(
            ServerSideEncryptionRule
              .builder()
              .applyServerSideEncryptionByDefault(
                ServerSideEncryptionByDefault.builder().sseAlgorithm("AES256").build()
              )
              .build()
          )
          .build()
      )
      .build()

  "getBucketEncryption" - {
    "returns Encrypted when a server-side encryption configuration is present" in {
      val client = stubClient(_ => encryptedResponse)
      S3.getBucketEncryption(client, "bucket").value() shouldBe Encrypted
    }

    "returns NotEncrypted when the encryption configuration is absent" in {
      val client = stubClient(_ => GetBucketEncryptionResponse.builder().build())
      S3.getBucketEncryption(client, "bucket").value() shouldBe NotEncrypted
    }

    "returns NotEncrypted for the ServerSideEncryptionConfigurationNotFoundError code" in {
      val client = stubClient(_ => throw s3ExceptionWithCode("ServerSideEncryptionConfigurationNotFoundError"))
      S3.getBucketEncryption(client, "bucket").value() shouldBe NotEncrypted
    }

    "returns BucketNotFound for the NoSuchBucket code" in {
      val client = stubClient(_ => throw s3ExceptionWithCode("NoSuchBucket"))
      S3.getBucketEncryption(client, "bucket").value() shouldBe BucketNotFound
    }

    "maps AccessDenied to EncryptionUnknown rather than failing the attempt" in {
      val client = stubClient(_ => throw s3ExceptionWithCode("AccessDenied"))
      S3.getBucketEncryption(client, "bucket").value() shouldBe EncryptionUnknown
    }

    "results in a FailedAttempt for an unexpected S3Exception error code" in {
      val client = stubClient(_ => throw s3ExceptionWithCode("SomethingUnexpected"))
      S3.getBucketEncryption(client, "bucket").isFailedAttempt() shouldBe true
    }

    "results in a FailedAttempt when the S3Exception has no error details (null errorCode)" in {
      // An S3Exception with no awsErrorDetails must not throw a NullPointerException
      // in the guard; it should fall through to the NonFatal handler.
      val client = stubClient(_ => throw S3Exception.builder().build().asInstanceOf[S3Exception])
      S3.getBucketEncryption(client, "bucket").isFailedAttempt() shouldBe true
    }
  }
}
