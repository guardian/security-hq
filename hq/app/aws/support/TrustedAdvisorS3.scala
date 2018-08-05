package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import com.amazonaws.regions.Regions
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model.{AwsAccount, PublicS3BucketDetail, TrustedAdvisorDetailsResult}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object TrustedAdvisorS3 {
  private val S3_Bucket_Permissions = "Pfx0RwqBli"

  def getAllPublicBuckets(accounts: List[AwsAccount], taClients: Map[(String, Regions), AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[PublicS3BucketDetail]])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        publicBucketsForAccount(account, taClients).asFuture.map(account -> _)
      }
    }
  }

  private def getPublicS3Buckets(client: AWSSupportAsync)(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[PublicS3BucketDetail]] = {
    getTrustedAdvisorCheckDetails(client, S3_Bucket_Permissions)
      .flatMap(parseTrustedAdvisorCheckResult(parsePublicS3BucketDetail, ec))
  }

  private def publicBucketsForAccount(account: AwsAccount, taClients: Map[(String, Regions), AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[PublicS3BucketDetail]] = {
    for {
      supportClient <- TrustedAdvisor.client(taClients, account)
      exposedIamKeysResult <- getPublicS3Buckets(supportClient)
      exposedIamKeys = exposedIamKeysResult.flaggedResources
    } yield exposedIamKeys
  }

  private[support] def parsePublicS3BucketDetail(detail: TrustedAdvisorResourceDetail): Attempt[PublicS3BucketDetail] = {
    detail.getMetadata.asScala.toList match {
      case region :: _ :: bucketName :: aclAllowsRead :: aclAllowsWrite :: status :: policyAllowsAccess ::  _ =>
        Attempt.Right {
          PublicS3BucketDetail(
            region,
            bucketName,
            aclAllowsRead,
            aclAllowsWrite,
            status,
            policyAllowsAccess,
            isSuppressed = detail.getIsSuppressed
          )
        }
      case metadata =>
        Attempt.Left {
          Failure(s"Could not parse S3 Bucket report from TrustedAdvisorResourceDetail with metadata $metadata", "Could not parse public S3 Buckets", 500).attempt
        }
    }
  }

}