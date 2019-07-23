package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model._
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object TrustedAdvisorS3 {
  private val S3_Bucket_Permissions = "Pfx0RwqBli"

  def getAllPublicBuckets(accounts: List[AwsAccount], taClients: AwsClients[AWSSupportAsync], s3Clients: AwsClients[AmazonS3])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[BucketDetail]])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        publicBucketsForAccount(account, taClients, s3Clients).asFuture.map(account -> _)
      }
    }
  }

  private def getBucketReport(client: AwsClient[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[BucketDetail]] = {
    getTrustedAdvisorCheckDetails(client, S3_Bucket_Permissions)
      .flatMap(parseTrustedAdvisorCheckResult(parseBucketDetail, ec))
  }

//  When you use server-side encryption, Amazon S3 encrypts an object before saving
//  it to disk in its data centers and decrypts it when you download the object
  private def addEncryptionStatus(bucket: BucketDetail, client: AwsClient[AmazonS3])(implicit ec: ExecutionContext): BucketDetail = {
    // If there is no bucket encryption, AWS returns an error...
    // Assume bucket is not encrypted if we cannot successfully getBucketEncryption
    try {
      client.client.getBucketEncryption(bucket.bucketName)
      bucket.copy(isEncrypted = true)
    } catch {
        case e: AmazonS3Exception => {
          bucket
        }
    }
  }

  private def publicBucketsForAccount(account: AwsAccount, taClients: AwsClients[AWSSupportAsync], s3Clients: AwsClients[AmazonS3])(implicit ec: ExecutionContext): Attempt[List[BucketDetail]] = {
    for {
      supportClient <- taClients.get(account)
      s3Client <- s3Clients.get(account)
      bucketResult <- getBucketReport(supportClient)
      enhancedBuckets = bucketResult.flaggedResources.map(addEncryptionStatus(_, s3Client))
    } yield enhancedBuckets
  }

  private[support] def parseBucketDetail(detail: TrustedAdvisorResourceDetail): Attempt[BucketDetail] = {
    def toBoolean(str: String): Boolean = str.toLowerCase.contentEquals("yes")

    detail.getMetadata.asScala.toList match {
      case region :: _ :: bucketName :: aclAllowsRead :: aclAllowsWrite :: status :: policyAllowsAccess ::  _ =>
        Attempt.Right {
          BucketDetail(
            region,
            bucketName,
            status.toLowerCase,
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