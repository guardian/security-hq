package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import aws.{AwsClient, AwsClients}
import com.amazonaws.regions.Regions
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model.{AwsAccount, ExposedIAMKeyDetail, TrustedAdvisorDetailsResult}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}


object TrustedAdvisorExposedIAMKeys {
  val AWS_EXPOSED_ACCESS_KEYS_IDENTIFIER = "12Fnkpl8Y5"

  def getAllExposedKeys(accounts: List[AwsAccount], taClients: AwsClients[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        exposedKeysForAccount(account, taClients).asFuture.map(account -> _)
      }
    }
  }

  private def getExposedIAMKeys(client: AwsClient[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[ExposedIAMKeyDetail]] = {
    getTrustedAdvisorCheckDetails(client, AWS_EXPOSED_ACCESS_KEYS_IDENTIFIER)
      .flatMap(parseTrustedAdvisorCheckResult(parseExposedIamKeyDetail, ec))
  }

  private def exposedKeysForAccount(account: AwsAccount, taClients: AwsClients[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[ExposedIAMKeyDetail]] = {
    for {
      supportClient <- taClients.get(account)
      exposedIamKeysResult <- TrustedAdvisorExposedIAMKeys.getExposedIAMKeys(supportClient)
      exposedIamKeys = exposedIamKeysResult.flaggedResources
    } yield exposedIamKeys
  }

  private[support] def parseExposedIamKeyDetail(detail: TrustedAdvisorResourceDetail): Attempt[ExposedIAMKeyDetail] = {
    detail.getMetadata.asScala.toList match {
      case keyId :: username :: fraudType :: caseId :: updated :: location :: deadline :: usage :: _ =>
        Attempt.Right {
          ExposedIAMKeyDetail(keyId, username, fraudType, caseId, updated, location, deadline, usage)
        }
      case metadata =>
        Attempt.Left {
          Failure(s"Could not parse IAM credentials report from TrustedAdvisorResourceDetail with metadata $metadata", "Could not parse exposed IAM keys", 500).attempt
        }
    }
  }
}
