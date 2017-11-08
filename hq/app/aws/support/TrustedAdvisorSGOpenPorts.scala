package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult}
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.TrustedAdvisorResourceDetail
import model.{SGOpenPortsDetail, TrustedAdvisorDetailsResult}
import utils.attempt.{Attempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object TrustedAdvisorSGOpenPorts {
  val AWS_SECURITY_GROUPS_PORTS_UNRESTRICTED_IDENTIFIER = "HCP4007jGY"
  val SGIds = "^(sg-[\\w]+) \\((vpc-[\\w]+)\\)$".r

  def getSGOpenPorts(client: AWSSupportAsync)(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[SGOpenPortsDetail]] = {
    getTrustedAdvisorCheckDetails(client, AWS_SECURITY_GROUPS_PORTS_UNRESTRICTED_IDENTIFIER)
      .flatMap(parseTrustedAdvisorCheckResult(parseSGOpenPortsDetail, ec))
  }

  def sgIds(result: TrustedAdvisorDetailsResult[SGOpenPortsDetail]): List[String] = {
    result.flaggedResources.map(_.id)
  }


  private[support] def parseSGOpenPortsDetail(detail: TrustedAdvisorResourceDetail): Attempt[SGOpenPortsDetail] = {
    detail.getMetadata.asScala.toList match {
      case region :: name :: SGIds(sgId, vpcId) :: protocol :: alertLevel :: port :: _ =>
        Attempt.Right {
          SGOpenPortsDetail(
            status = detail.getStatus,
            region = detail.getRegion,
            name = name,
            id = sgId,
            vpcId = vpcId,
            protocol = protocol,
            port = port,
            alertLevel = alertLevel,
            isSuppressed = detail.getIsSuppressed
          )
        }
      case region :: name :: sgId :: protocol :: alertLevel :: port :: _ =>
        Attempt.Right {
          SGOpenPortsDetail(
            status = detail.getStatus,
            region = detail.getRegion,
            name = name,
            id = sgId,
            vpcId = "EC2 classic",
            protocol = protocol,
            port = port,
            alertLevel = alertLevel,
            isSuppressed = detail.getIsSuppressed
          )
        }
      case metadata =>
        Attempt.Left {
          Failure(s"Could not parse SGOpenPorts from TrustedAdvisorResourceDetail with metadata $metadata", "Could not parse SGOpenPorts result", 500).attempt
        }
    }
  }
}
