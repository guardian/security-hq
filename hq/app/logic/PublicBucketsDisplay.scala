package logic

import java.net.URLEncoder

import model._
import utils.attempt.FailedAttempt

object PublicBucketsDisplay {

  case class BucketReportSummary(total: Int, warnings: Int, errors: Int, suppressed: Int)

  def reportSummary(buckets: List[BucketDetail]): BucketReportSummary = {
    val (suppressed, flagged) = buckets.partition( b => b.isSuppressed)
    val reportStatusSummary = flagged.map( bucket => bucketReportStatus(bucket))

    val (warnings, errors) = reportStatusSummary.foldLeft(0,0) {
      case ( (war, err), Amber ) => (war+1, err)
      case ( (war, err), Red ) => (war, err+1)
      case ( (war, err), _ ) => (war, err)
    }
    BucketReportSummary(buckets.length, warnings, errors, suppressed.length)
  }

  def hasSuppressedReports(buckets: List[BucketDetail]): Boolean = buckets.exists(_.isSuppressed)

  def linkForAwsConsole(bucket: BucketDetail): String = {
    s"https://console.aws.amazon.com/s3/home?bucket=${URLEncoder.encode(bucket.bucketName, "utf-8")}"
  }

  private[logic] def bucketReportStatus(bucket: BucketDetail): ReportStatus = {
    // permission properties that grant global access
    // The bucket ACL allows Upload/Delete access to anyone
    if (bucket.aclAllowsWrite)
      Red
    // The bucket ACL allows List access to anyone, or a bucket policy allows any kind of open access
    else if (bucket.aclAllowsRead || bucket.policyAllowsAccess)
      Amber
    else Green
  }

  private def bucketDetailsSort(bucketDetail: BucketDetail): (Int, String) = {
    val severity = bucketDetail.reportStatus.getOrElse(10) match {
      case Red => 0
      case Amber => 1
      case Green => 2
      case Blue => 3
    }
    (severity, bucketDetail.bucketName)
  }

  private def accountsSort(accountInfo: (AwsAccount, Either[FailedAttempt, (BucketReportSummary, List[BucketDetail])])): (Int, Int, Int, String) = {
    accountInfo match {
      case (account, Right((bucketReportSummary, _))) =>
        (-bucketReportSummary.errors, -bucketReportSummary.warnings, -bucketReportSummary.suppressed, account.name)
      case (account, _) =>
        (0, 0, 0, account.name)
    }
  }

  def accountsBucketData(reports: List[(AwsAccount, Either[FailedAttempt, List[BucketDetail]])]):
      List[(AwsAccount, Either[FailedAttempt, (BucketReportSummary, List[BucketDetail])])] = {
    reports.map(accountBucketData).sortBy(accountsSort)
  }

  def accountBucketData(accountInfo: (AwsAccount, Either[FailedAttempt, List[BucketDetail]])):
      (AwsAccount, Either[FailedAttempt, (BucketReportSummary, List[BucketDetail])]) = {
    accountInfo match {
      case (account, Right(bucketDetails)) =>
        (account, Right(reportSummary(bucketDetails), bucketDetails.sortBy(bucketDetailsSort)))
      case (account, Left(failure)) => (account, Left(failure))
    }
  }
}
