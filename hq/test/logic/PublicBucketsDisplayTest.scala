package logic

import logic.PublicBucketsDisplay._
import model._
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt.Failure

class PublicBucketsDisplayTest extends FreeSpec with Matchers {

  def createExampleBuckets(id: Int): List[BucketDetail] = {
    List(
      BucketDetail("eu-west-1", s"bucket-green-$id", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true),
      BucketDetail("eu-west-1", s"bucket-amber-$id", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber), isEncrypted = true),
      BucketDetail("eu-west-1", s"bucket-red-$id", "Red", aclAllowsRead = false, aclAllowsWrite = true, policyAllowsAccess = false, isSuppressed = false, Some(Red()), isEncrypted = true)
    )
  }

  "reportSummary" - {
    "counts the total reports along with warnings and errors" in {
      val examples: List[BucketDetail] = createExampleBuckets(1) ++ createExampleBuckets(2)

      reportSummary(examples) shouldEqual BucketReportSummary(6, 2, 2, 0, 0)
    }

    "includes the number of suppressed reports" in {
      val examples: List[BucketDetail] =
        createExampleBuckets(1) ++
          List(BucketDetail("eu-west-1", s"bucket-suppressed-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = true, Some(Amber), isEncrypted = true),
            BucketDetail("eu-west-1", s"bucket-suppressed-2", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = true, Some(Amber), isEncrypted = true)
          )

      reportSummary(examples) shouldEqual BucketReportSummary(5, 1, 1, 0, 2)
    }

    "includes the number of other issues, such as when bucket is not encrypted" in {
      val examples: List[BucketDetail] =
        createExampleBuckets(1) ++
          List(BucketDetail("eu-west-1", s"bucket-unencrypted-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Blue)),
            BucketDetail("eu-west-1", s"bucket-unencrypted-2", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Blue))
          )

      reportSummary(examples) shouldEqual BucketReportSummary(5, 1, 1, 2, 0)
    }
  }

  "bucketDetailsSort" - {
    "puts an amber bucket before green" in {
      val buckets = List(
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true),
        BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber))
      )

      buckets.sortBy(bucketDetailsSort) shouldEqual List(
        BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber)),
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true)
      )
    }

    "puts a blue bucket before green" in {
      val buckets = List(
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true),
        BucketDetail("eu-west-1", s"bucket-blue-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Blue))
      )

      buckets.sortBy(bucketDetailsSort) shouldEqual List(
        BucketDetail("eu-west-1", s"bucket-blue-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Blue)),
        BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true)
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
    val accountWithErrors = (AwsAccount("account-with-errors", "name", "ARN", "123456789"),
      Right(BucketReportSummary(3, 1, 2, 0, 0),
        List(
      BucketDetail("eu-west-1", s"bucket-red-1", "Red", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Red())),
      BucketDetail("eu-west-1", s"bucket-red-2", "Red", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = true, isSuppressed = false, Some(Red())),
      BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber))
    )))
    val accountWithWarnings = (AwsAccount("account-with-warnings", "name", "ARN", "123456789"),
      Right(BucketReportSummary(3, 2, 1, 0, 0),
        List(
      BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber)),
      BucketDetail("eu-west-1", s"bucket-amber-2", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Amber)),
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true)
    )))
    val accountWithOtherIssues = (AwsAccount("account-with-blue", "name", "ARN", "123456789"),
      Right(BucketReportSummary(2, 0, 0, 2, 0),
        List(
        BucketDetail("eu-west-1", s"bucket-blue-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None),
        BucketDetail("eu-west-1", s"bucket-blue-2", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None)
      )))
    val accountAllGreen = (AwsAccount("account-all-green", "name", "ARN", "123456789"),
      Right(BucketReportSummary(2, 0, 0, 0, 0),
        List(
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true),
      BucketDetail("eu-west-1", s"bucket-green-2", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, Some(Green), isEncrypted = true)
    )))

    "puts an account with errors above one with lots of warnings" in {
      val accounts = List(accountWithWarnings, accountAllGreen, accountWithErrors).sortBy(accountsSort)
      accounts shouldEqual List(accountWithErrors, accountWithWarnings, accountAllGreen)
    }

    "puts an account with other issues after those with errors and warnings" in {
      val accounts = List(accountWithOtherIssues, accountWithErrors, accountWithWarnings).sortBy(accountsSort)
      accounts shouldEqual List(accountWithErrors, accountWithWarnings, accountWithOtherIssues)
    }

    "breaks ties by name" in {
      val accountA = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "first", "ARN", "123456789"))
      val accountB = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "second", "ARN", "123456789"))
      val accountC = accountWithWarnings.copy(_1 = AwsAccount("account-with-warnings", "will-be-last", "ARN", "123456789"))

      List(accountB, accountA, accountC).sortBy(accountsSort) shouldEqual List(
        accountA, accountB, accountC
      )
    }

    "puts an account with a failed response after others" in {
      val accountWithFailure = (
        AwsAccount("account-with-failure", "will-be-last", "ARN", "123456789"),
        Left(Failure.cacheServiceErrorPerAccount("account id", "failure type").attempt)
      )
      List(accountWithWarnings, accountWithFailure, accountWithOtherIssues, accountWithErrors).sortBy(accountsSort) shouldEqual List(
        accountWithErrors, accountWithWarnings, accountWithOtherIssues, accountWithFailure
      )
    }
  }

  val accountWithErrors = (AwsAccount("account-with-errors", "name", "ARN", "123456789"),
    Right(List(
      BucketDetail("eu-west-1", s"bucket-red-1", "Red", aclAllowsRead = false, aclAllowsWrite = true, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-red-2", "Red", aclAllowsRead = false, aclAllowsWrite = true, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None)
    )))
  val accountWithWarnings = (AwsAccount("account-with-warnings", "name", "ARN", "123456789"),
    Right(List(
      BucketDetail("eu-west-1", s"bucket-amber-1", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-amber-2", "Amber", aclAllowsRead = true, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None, isEncrypted = true)
    )))
  val accountWithOtherIssues = (AwsAccount("account-with-blue", "name", "ARN", "123456789"),
    Right(List(
      BucketDetail("eu-west-1", s"bucket-blue-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None),
      BucketDetail("eu-west-1", s"bucket-blue-2", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None)
    )))
  val accountAllGreen = (AwsAccount("account-all-green", "name", "ARN", "123456789"),
    Right(List(
      BucketDetail("eu-west-1", s"bucket-green-1", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None, isEncrypted = true),
      BucketDetail("eu-west-1", s"bucket-green-2", "Green", aclAllowsRead = false, aclAllowsWrite = false, policyAllowsAccess = false, isSuppressed = false, None, isEncrypted = true)
    )))

  "allAccountsBucketData" - {
    "sorts accounts according to severity of issues" in {
      val result = allAccountsBucketData(List(accountWithOtherIssues, accountWithWarnings, accountAllGreen, accountWithErrors))

      result.map(_._1) shouldEqual List(
        AwsAccount("account-with-errors", "name", "ARN", "123456789"),
        AwsAccount("account-with-warnings", "name", "ARN", "123456789"),
        AwsAccount("account-with-blue", "name", "ARN", "123456789"),
        AwsAccount("account-all-green", "name", "ARN", "123456789")
      )
    }
  }

  "accountBucketData" - {
    "adds bucket details summary information" in {
      val (greenSummary, _) = accountBucketData(accountAllGreen)._2.right.get
      val (amberSummary, _) = accountBucketData(accountWithWarnings)._2.right.get
      val (redSummary, _) = accountBucketData(accountWithErrors)._2.right.get
      val (blueSummary, _) = accountBucketData(accountWithOtherIssues)._2.right.get

      greenSummary shouldEqual BucketReportSummary(2, 0, 0, 0, 0)
      amberSummary shouldEqual BucketReportSummary(3, 2, 0, 0, 0)
      redSummary shouldEqual BucketReportSummary(3, 1, 2, 0, 0)
      blueSummary shouldEqual BucketReportSummary(2, 0, 0, 2, 0)
    }
  }
}
