package aws.support

import aws.s3.S3
import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import aws.{AwsClient, AwsClients, AwsClientsList}
import model.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.support.SupportAsyncClient
import software.amazon.awssdk.services.support.model.TrustedAdvisorResourceDetail
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.{Success, Try}

object TrustedAdvisorS3 {
  private val S3_Bucket_Permissions = "Pfx0RwqBli"

  def getAllPublicBuckets(accounts: List[AwsAccount], taClients: AwsClients[SupportAsyncClient], s3Clients: AwsClients[S3Client])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[BucketDetail]])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        publicBucketsForAccount(account, taClients, s3Clients).asFuture.map(account -> _)
      }
    }
  }

  private def getBucketReport(client: AwsClient[SupportAsyncClient])(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[BucketDetail]] = {
    getTrustedAdvisorCheckDetails(client, S3_Bucket_Permissions)
      .flatMap(parseTrustedAdvisorCheckResult(parseBucketDetail, ec))
  }

//  When you use server-side encryption, Amazon S3 encrypts an object before saving
//  it to disk in its data centers and decrypts it when you download the object
  private def addEncryptionStatus(bucket: BucketDetail, account: AwsAccount, clients: AwsClients[S3Client])(implicit ec: ExecutionContext): Attempt[Option[BucketDetail]] = {
    val tryFindEncryptionStatus =
      Try(Region.of(bucket.region)).map { regions =>
        clients.get(account, regions).flatMap { clientWrapper =>
          S3.getBucketEncryption(clientWrapper.client, bucket.bucketName)
        }
      }

    tryFindEncryptionStatus match {
      case Success(attempt) => attempt.map({
        case Encrypted => Some(bucket.copy(isEncrypted = true))
        case NotEncrypted => Some(bucket)
        case BucketNotFound => None
      })
      case scala.util.Failure(_) => Attempt.Left(FailedAttempt(Failure(
        s"Unrecognised region returned from Trusted Advisor for bucket ${bucket.bucketName}",
        "Encryption status for this bucket was unable to be fetched due to an unrecognised region being provided by Trusted Advisor.",
        500
      )))
    }
  }

  private def publicBucketsForAccount(account: AwsAccount, taClients: AwsClients[SupportAsyncClient], s3Clients: AwsClients[S3Client])(implicit ec: ExecutionContext): Attempt[List[BucketDetail]] = {
    for {
      supportClient <- taClients.get(account)
      bucketResult <- getBucketReport(supportClient)
      enhancedBuckets <- Attempt.traverse(bucketResult.flaggedResources)(addEncryptionStatus(_, account, s3Clients))
    } yield enhancedBuckets.flatten //remove buckets we weren't able to find encryption status for
  }

  private[support] def parseBucketDetail(detail: TrustedAdvisorResourceDetail): Attempt[BucketDetail] = {
    def toBoolean(str: String): Boolean = str.toLowerCase.contentEquals("yes")

    detail.metadata.asScala.toList match {
      case region :: _ :: bucketName :: aclAllowsRead :: aclAllowsWrite :: status :: policyAllowsAccess ::  _ =>
        Attempt.Right {
          BucketDetail(
            region,
            bucketName,
            status.toLowerCase,
            toBoolean(aclAllowsRead),
            toBoolean(aclAllowsWrite),
            toBoolean(policyAllowsAccess),
            isSuppressed = detail.isSuppressed,
            None
          )
        }
      case metadata =>
        Attempt.Left {
          utils.attempt.Failure(s"Could not parse S3 Bucket report from TrustedAdvisorResourceDetail with metadata $metadata", "Could not parse public S3 Buckets", 500).attempt
        }
    }
  }
}