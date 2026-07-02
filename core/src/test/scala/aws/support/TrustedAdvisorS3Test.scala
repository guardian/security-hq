package aws.support

import model.{BucketDetail, BucketNotFound, Encrypted, EncryptionUnknown, NotEncrypted}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class TrustedAdvisorS3Test extends AnyFreeSpec with Matchers {

  private val bucket = BucketDetail(
    region = "eu-west-1",
    bucketName = "example-bucket",
    status = "warning",
    aclAllowsRead = false,
    aclAllowsWrite = false,
    policyAllowsAccess = false,
    isSuppressed = false
  )

  "bucketForEncryptionStatus" - {
    "marks the bucket as encrypted when Encrypted" in {
      TrustedAdvisorS3.bucketForEncryptionStatus(bucket, Encrypted) shouldBe Some(bucket.copy(isEncrypted = true))
    }

    "keeps the unmodified bucket when NotEncrypted" in {
      TrustedAdvisorS3.bucketForEncryptionStatus(bucket, NotEncrypted) shouldBe Some(bucket)
    }

    "keeps the bucket in the report when the encryption status is unknown" in {
      // A single bucket whose status could not be determined (for example, an
      // access-denied error) must not disappear from the report.
      TrustedAdvisorS3.bucketForEncryptionStatus(bucket, EncryptionUnknown) shouldBe Some(bucket)
    }

    "drops the bucket when it no longer exists" in {
      TrustedAdvisorS3.bucketForEncryptionStatus(bucket, BucketNotFound) shouldBe None
    }
  }
}
