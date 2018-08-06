package logic

import java.net.URLEncoder

import model._

object PublicBucketsDisplay {

  case class BucketReportSummary(total: Int, warnings: Int, errors: Int, suppressed: Int)

  def reportSummary(buckets: List[PublicS3BucketDetail]): BucketReportSummary = {
    val (suppressed, flagged) = buckets.partition( b => b.isSuppressed)
    val reportStatusSummary = flagged.map( bucket => bucketReportStatus(bucket))

    val (warnings, errors) = reportStatusSummary.foldLeft(0,0) {
      case ( (war, err), Amber ) => (war+1, err)
      case ( (war, err), Red ) => (war, err+1)
      case ( (war, err), _ ) => (war, err)
    }
    BucketReportSummary(buckets.length, warnings, errors, suppressed.length)
  }

  def hasSuppressedReports(buckets: List[PublicS3BucketDetail]): Boolean = buckets.exists(_.isSuppressed)

  def linkForAwsConsole(bucket: PublicS3BucketDetail): String = {
    s"https://console.aws.amazon.com/s3/home?bucket=${URLEncoder.encode(bucket.bucketName, "utf-8")}"
  }

  private[logic] def bucketReportStatus(bucket: PublicS3BucketDetail): ReportStatus = {
    // permission properties that grant global access
    // The bucket ACL allows Upload/Delete access to anyone
    if (bucket.aclAllowsWrite)
      Red
    // The bucket ACL allows List access to anyone, or a bucket policy allows any kind of open access
    else if (bucket.aclAllowsRead || bucket.policyAllowsAccess)
      Amber
    else Green
  }
}
