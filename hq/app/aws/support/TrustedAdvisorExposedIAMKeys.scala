package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model.{ExposedIAMKeyDetail, TrustedAdvisorDetailsResult}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object TrustedAdvisorExposedIAMKeys {
  val exposedIAMKeys = "12Fnkpl8Y5"

  def getExposedIAMKeys(client: AWSSupportAsync)(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[ExposedIAMKeyDetail]] = {
    getTrustedAdvisorCheckDetails(client, exposedIAMKeys)
      .map(parseTrustedAdvisorCheckResult(parseExposedIamKeyDetail))
  }


  private[support] def parseExposedIamKeyDetail(detail: TrustedAdvisorResourceDetail): ExposedIAMKeyDetail = {
    detail.getMetadata.asScala.toList match {
      case keyId :: username :: fraudType :: caseId :: updated :: location :: deadline :: usage :: _ =>
        ExposedIAMKeyDetail(keyId, username, fraudType, caseId, updated, location, deadline, usage)
      case metadata =>
        throw new RuntimeException(s"Could not parse SGOpenPorts from TrustedAdvisorResourceDetail with metadata $metadata")
    }
  }
}
