package logic

import org.scalatest.{FreeSpec, Matchers}
import PublicBucketsDisplay._
import model._
import utils.attempt.{FailedAttempt, Failure}

class PublicBucketsDisplayTest extends FreeSpec with Matchers {

  def createExampleBuckets(id: Int): List[BucketDetail] = {
    List(
      BucketDetail("eu-west-1", s"bucket-green-$id", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green)),
      BucketDetail("eu-west-1", s"bucket-amber-$id", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber)),
      BucketDetail("eu-west-1", s"bucket-red-$id", "Red", aclAllowsRead = false, aclAllowsWrite = true, policyAllowsAccess = false, isSuppressed = false, Some(Red))
    )
  }

  "reportSummary" - {
    "lis" in {
      val examples: List[BucketDetail] = createExampleBuckets(1) ++ createExampleBuckets(2)

      reportSummary(examples) shouldEqual BucketReportSummary(6, 2, 2, 0)
    }
  }

  "bucketDetailsSort" - {

    "puts an amber bucket before green" in {
      val buckets = List(
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green)),
        BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber))
      )

      buckets.sortBy(bucketDetailsSort) shouldEqual List(
        BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber)),
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green))
      )
    }

    "puts a green bucket before blue" in {
      val buckets = List(
        BucketDetail("eu-west-1", s"bucket-blue-1", "Blue", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Blue)),
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green))
      )

      buckets.sortBy(bucketDetailsSort) shouldEqual List(
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green)),
        BucketDetail("eu-west-1", s"bucket-blue-1", "Blue", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Blue))
      )
    }

    "puts a Red bucket before all others" in {
      val buckets = createExampleBuckets(1) ++ createExampleBuckets(2)
      buckets.sortBy(bucketDetailsSort).map(_.bucketName) shouldEqual List(
        "bucket-red-1",
        "bucket-red-2",
        "bucket-amber-1",
        "bucket-amber-2",
        "bucket-green-1",
        "bucket-green-2",
      )
    }
  }

  "accountsSort" - {
    val accountWithErrors = (AwsAccount("account-with-errors", "name", "ARN"),
      Right(BucketReportSummary(3, 1, 2, 0),
        List(
      BucketDetail("eu-west-1", s"bucket-red-1", "Red", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Red)),
      BucketDetail("eu-west-1", s"bucket-red-2", "Red", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Red)),
      BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber))
    )))
    val accountWithWarnings = (AwsAccount("account-with-warnings", "name", "ARN"),
      Right(BucketReportSummary(3, 2, 1, 0),
        List(
      BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber)),
      BucketDetail("eu-west-1", s"bucket-amber-2", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber)),
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green))
    )))
    val accountAllGreen = (AwsAccount("account-all-green", "name", "ARN"),
      Right(BucketReportSummary(2, 0, 0, 0),
        List(
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green)),
      BucketDetail("eu-west-1", s"bucket-green-2", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green))
    )))
    "puts an account with errors above one with lots of warnings" in {
      val accounts = List(accountWithWarnings, accountAllGreen, accountWithErrors).sortBy(accountsSort)
      accounts shouldEqual List(accountWithErrors, accountWithWarnings, accountAllGreen)
    }

    "breaks ties by name" in {
      val accountA = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "first", "ARN"))
      val accountB = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "second", "ARN"))
      val accountC = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "will-be-last", "ARN"))

      List(accountB, accountA, accountC).sortBy(accountsSort) shouldEqual List(
        accountA, accountB, accountC
      )
    }

    "puts an account with a failed response after others" ignore {
      val accountA = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "first", "ARN"))
      val accountB = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "second", "ARN"))
      val accountWithFailure = (
        AwsAccount("account-with-failure", "will-be-last", "ARN"),
        Left(Failure.cacheServiceErrorPerAccount("account id", "failure type").attempt)
      )
      List(accountB, accountWithFailure, accountA).sortBy(accountsSort) shouldEqual List(
        accountA, accountB, accountWithFailure
      )
    }
  }

  "allAccountsBucketData" - {
    "sorts accounts" ignore {

    }
  }

  "accountBucketData" - {
    val accountWithErrors = (AwsAccount("account-with-errors", "name", "ARN"),
      Right(List(
        BucketDetail("eu-west-1", s"bucket-red-1", "Red", aclAllowsRead = false, aclAllowsWrite = true, policyAllowsAccess = false, isSuppressed = false, None),
        BucketDetail("eu-west-1", s"bucket-red-2", "Red", aclAllowsRead = false, aclAllowsWrite = true, policyAllowsAccess = false, isSuppressed = false, None),
        BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None)
      )))
    val accountWithWarnings = (AwsAccount("account-with-warnings", "name", "ARN"),
      Right(List(
      BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-amber-2", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None)
    )))
    val accountAllGreen = (AwsAccount("account-all-green", "name", "ARN"),
      Right(List(
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-green-2", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None)
    )))

    "sorts buckets" ignore {
      val accounts = List(accountWithWarnings, accountAllGreen, accountWithErrors)
    }

    "adds bucket details summary information" in {
      val (greenSummary, _) = accountBucketData(accountAllGreen)._2.right.get
      val (amberSummary, _) = accountBucketData(accountWithWarnings)._2.right.get
      val (redSummary, _) = accountBucketData(accountWithErrors)._2.right.get

      greenSummary shouldEqual BucketReportSummary(2, 0, 0, 0)
      amberSummary shouldEqual BucketReportSummary(3, 2, 0, 0)
      redSummary shouldEqual BucketReportSummary(3, 1, 2, 0)
    }

    "adds the Report details summary information" in {

    }
  }
}
