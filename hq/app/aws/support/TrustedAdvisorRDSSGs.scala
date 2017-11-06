package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model.{RDSSGsDetail, TrustedAdvisorDetailsResult}
import utils.attempt.{Attempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object TrustedAdvisorRDSSGs {
  val rdsSGs = "nNauJisYIT"

  def getRDSSecurityGroupDetail(client: AWSSupportAsync)(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[RDSSGsDetail]] = {
    getTrustedAdvisorCheckDetails(client, rdsSGs)
      .flatMap(parseTrustedAdvisorCheckResult(parseRDSSGDetail, ec))
  }


  private[support] def parseRDSSGDetail(detail: TrustedAdvisorResourceDetail): Attempt[RDSSGsDetail] = {
    detail.getMetadata.asScala.toList match {
      case region :: rdsSgId :: ec2SGId :: alertLevel :: _ =>
        Attempt.Right {
          RDSSGsDetail(
            region = detail.getRegion,
            rdsSgId = rdsSgId,
            ec2SGId = ec2SGId,
            alertLevel = alertLevel,
            isSuppressed = detail.getIsSuppressed
          )
        }
      case metadata =>
        Attempt.Left {
          Failure(s"Could not parse RDSSGs from TrustedAdvisorResourceDetail with metadata $metadata", "Could not parse RDS Security group information", 500).attempt
        }
    }
  }
}
