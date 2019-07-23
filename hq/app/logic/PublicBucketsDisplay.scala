package logic

import java.net.URLEncoder

import model._
import utils.attempt.FailedAttempt

object PublicBucketsDisplay {

  case class BucketReportSummary(total: Int, warnings: Int, errors: Int, other: Int, suppressed: Int)

  def reportSummary(buckets: List[BucketDetail]): BucketReportSummary = {
    val (suppressed, flagged) = buckets.partition( b => b.isSuppressed)
    val reportStatusSummary = flagged.map( bucket => bucketReportStatus(bucket))

    val (warnings, errors, other) = reportStatusSummary.foldLeft(0,0,0) {
      case ( (war, err, oth), Amber ) => (war+1, err, oth)
      case ( (war, err, oth), Red ) => (war, err+1, oth)
      case ( (war, err, oth), Blue ) => (war, err, oth+1)
      case ( (war, err, oth), _ ) => (war, err, oth)
    }
    BucketReportSummary(buckets.length, warnings, errors, other, suppressed.length)
  }

  def hasSuppressedReports(buckets: List[BucketDetail]): Boolean = buckets.exists(_.isSuppressed)

  def linkForAwsConsole(bucket: BucketDetail): String = {
    s"https://console.aws.amazon.com/s3/home?bucket=${URLEncoder.encode(bucket.bucketName, "utf-8")}"
  }

  def isOnlyMissingEncryption(bucket: BucketDetail): Boolean = {
    if (bucket.reportStatus.getOrElse(None) == Blue) {
      return !bucket.isEncrypted
    }
    false
  }

  private[logic] def bucketReportStatus(bucket: BucketDetail): ReportStatus = {
    // permission properties that grant global access
    // The bucket ACL allows Upload/Delete access to anyone
    if (bucket.aclAllowsWrite)
      Red
    // The bucket ACL allows List access to anyone, or a bucket policy allows any kind of open access
    else if (bucket.aclAllowsRead || bucket.policyAllowsAccess)
      Amber
    else if (!bucket.isEncrypted)
      Blue
    else Green
  }

  private[logic] def bucketDetailsSort(bucketDetail: BucketDetail): (Int, String) = {
    val severity = bucketDetail.reportStatus.getOrElse(Blue) match {
      case Red => 0
      case Amber => 1
      case Blue => 2
      case Green => 3
    }
    (severity, bucketDetail.bucketName)
  }

  private[logic] def accountsSort(accountInfo: (AwsAccount, Either[FailedAttempt, (BucketReportSummary, List[BucketDetail])])): (Int, Int, Int, Int, String) = {
    accountInfo match {
      case (account, Right((bucketReportSummary, _))) =>
        (-bucketReportSummary.errors, -bucketReportSummary.warnings, -bucketReportSummary.other, -bucketReportSummary.suppressed, account.name)
      case (account, _) =>
        (0, 0, 0, 0, account.name)
    }
  }

  // The TA report actually includes all buckets, even if they are status green
  private def removeGreenBuckets(buckets: List[BucketDetail]): List[BucketDetail] = {
    buckets.filter( _.reportStatus.getOrElse(Blue) != Green )
  }

  def allAccountsBucketData(reports: List[(AwsAccount, Either[FailedAttempt, List[BucketDetail]])]):
      List[(AwsAccount, Either[FailedAttempt, (BucketReportSummary, List[BucketDetail])])] = {
    reports.map(accountBucketData).sortBy(accountsSort)
  }

  def accountBucketData(accountInfo: (AwsAccount, Either[FailedAttempt, List[BucketDetail]])):
      (AwsAccount, Either[FailedAttempt, (BucketReportSummary, List[BucketDetail])]) = {
    accountInfo match {
      case (account, Right(bucketDetails)) => {
        val bucketsWithReportStatus = bucketDetails.map(
          bucket => bucket.copy(reportStatus = Some(bucketReportStatus(bucket)))
        )
        val sortedAndFilteredBuckets = removeGreenBuckets(bucketsWithReportStatus).sortBy(bucketDetailsSort)
        (account, Right(reportSummary(bucketDetails), sortedAndFilteredBuckets))
      }
      case (account, Left(failure)) => (account, Left(failure))
    }
  }
}
