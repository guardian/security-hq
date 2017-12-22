package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model.{AwsAccount, ExposedIAMKeyDetail, TrustedAdvisorDetailsResult}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object TrustedAdvisorExposedIAMKeys {
  val AWS_EXPOSED_ACCESS_KEYS_IDENTIFIER = "12Fnkpl8Y5"

  def getExposedIAMKeys(client: AWSSupportAsync)(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[ExposedIAMKeyDetail]] = {
    getTrustedAdvisorCheckDetails(client, AWS_EXPOSED_ACCESS_KEYS_IDENTIFIER)
      .flatMap(parseTrustedAdvisorCheckResult(parseExposedIamKeyDetail, ec))
  }

  def getAllAccountsExposedIAMKeys(accounts: List[AwsAccount])(implicit ec: ExecutionContext): Attempt[List[Either[FailedAttempt, (AwsAccount, TrustedAdvisorDetailsResult[ExposedIAMKeyDetail])]]] = {
    Attempt.traverseWithFailures(accounts) { account =>
      val client = TrustedAdvisor.client(account)
      for {
        exposedIamKeysResult <- getExposedIAMKeys(client)
      } yield account -> exposedIamKeysResult
    }
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
