package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import aws.{AwsClient, AwsClients}
import com.amazonaws.regions.Regions
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model._
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object TrustedAdvisorS3 {
  private val S3_Bucket_Permissions = "Pfx0RwqBli"

  def getAllPublicBuckets(accounts: List[AwsAccount], taClients: AwsClients[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[PublicS3BucketDetail]])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        publicBucketsForAccount(account, taClients).asFuture.map(account -> _)
      }
    }
  }

  private def getPublicS3Buckets(client: AwsClient[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[PublicS3BucketDetail]] = {
    getTrustedAdvisorCheckDetails(client, S3_Bucket_Permissions)
      .flatMap(parseTrustedAdvisorCheckResult(parsePublicS3BucketDetail, ec))
  }

  private def publicBucketsForAccount(account: AwsAccount, taClients: AwsClients[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[PublicS3BucketDetail]] = {
    for {
      supportClient <- taClients.get(account)
      publicBucketsResult <- getPublicS3Buckets(supportClient)
      publicBuckets = publicBucketsResult.flaggedResources
    } yield publicBuckets
  }

  private[support] def parsePublicS3BucketDetail(detail: TrustedAdvisorResourceDetail): Attempt[PublicS3BucketDetail] = {
    def toBoolean(str: String): Boolean = str.toLowerCase.contentEquals("yes")

    detail.getMetadata.asScala.toList match {
      case region :: _ :: bucketName :: aclAllowsRead :: aclAllowsWrite :: status :: policyAllowsAccess ::  _ =>
        Attempt.Right {
          PublicS3BucketDetail(
            region,
            bucketName,
            status,
            toBoolean(aclAllowsRead),
            toBoolean(aclAllowsWrite),
            toBoolean(policyAllowsAccess),
            isSuppressed = detail.getIsSuppressed,
            None
          )
        }
      case metadata =>
        Attempt.Left {
          Failure(s"Could not parse S3 Bucket report from TrustedAdvisorResourceDetail with metadata $metadata", "Could not parse public S3 Buckets", 500).attempt
        }
    }
  }
}