package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model.{RDSSGsDetail, TrustedAdvisorDetailsResult}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object TrustedAdvisorRDSSGs {
  val rdsSGs = "nNauJisYIT"

  def getRDSSecurityGroupDetail(client: AWSSupportAsync)(implicit ec: ExecutionContext): Future[TrustedAdvisorDetailsResult[RDSSGsDetail]] = {
    getTrustedAdvisorCheckDetails(client, rdsSGs)
      .map(parseTrustedAdvisorCheckResult(parseRDSSGDetail))
  }

  def parseRDSSGDetail(detail: TrustedAdvisorResourceDetail): RDSSGsDetail = {
    detail.getMetadata.asScala.toList match {
      case region :: rdsSgId :: ec2SGId :: alertLevel :: _ =>
        RDSSGsDetail(
          region = detail.getRegion,
          rdsSgId = rdsSgId,
          ec2SGId = ec2SGId,
          alertLevel = alertLevel,
          isSuppressed = detail.getIsSuppressed
        )
      case metadata =>
        throw new RuntimeException(s"Could not parse RDSSGs from TrustedAdvisorResourceDetail with metadata $metadata")
    }
  }
}
