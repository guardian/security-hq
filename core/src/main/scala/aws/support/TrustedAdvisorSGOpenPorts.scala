package aws.support

import aws.support.TrustedAdvisor.{getTrustedAdvisorCheckDetails, parseTrustedAdvisorCheckResult, refreshTrustedAdvisorChecks}
import aws.AwsClient
import logic.Retry
import model.{SGOpenPortsDetail, TrustedAdvisorDetailsResult}
import utils.attempt.{Attempt, Failure}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import software.amazon.awssdk.services.support.SupportAsyncClient
import software.amazon.awssdk.services.support.model.{RefreshTrustedAdvisorCheckResponse, TrustedAdvisorResourceDetail}


object TrustedAdvisorSGOpenPorts {
  val AWS_SECURITY_GROUPS_PORTS_UNRESTRICTED_IDENTIFIER = "HCP4007jGY"
  val SGIds = "^(sg-[\\w]+) \\((vpc-[\\w]+)\\)$".r

  def getSGOpenPorts(client: AwsClient[SupportAsyncClient])(implicit ec: ExecutionContext): Attempt[TrustedAdvisorDetailsResult[SGOpenPortsDetail]] = {
    getTrustedAdvisorCheckDetails(client, AWS_SECURITY_GROUPS_PORTS_UNRESTRICTED_IDENTIFIER)
      .flatMap(parseTrustedAdvisorCheckResult(parseSGOpenPortsDetail, ec))
  }

  def sgIds(result: TrustedAdvisorDetailsResult[SGOpenPortsDetail]): List[String] = {
    result.flaggedResources.map(_.id)
  }


  private[support] def parseSGOpenPortsDetail(detail: TrustedAdvisorResourceDetail): Attempt[SGOpenPortsDetail] = {
    detail.metadata.asScala.toList match {
      case region :: name :: SGIds(sgId, vpcId) :: protocol :: alertLevel :: port :: _ =>
        Attempt.Right {
          SGOpenPortsDetail(
            status = detail.status,
            region = detail.region,
            name = name,
            id = sgId,
            vpcId = vpcId,
            protocol = protocol,
            port = port,
            alertLevel = alertLevel,
            isSuppressed = detail.isSuppressed
          )
        }
      case region :: name :: sgId :: protocol :: alertLevel :: port :: _ =>
        Attempt.Right {
          SGOpenPortsDetail(
            status = detail.status,
            region = detail.region,
            name = name,
            id = sgId,
            vpcId = "EC2 classic",
            protocol = protocol,
            port = port,
            alertLevel = alertLevel,
            isSuppressed = detail.isSuppressed
          )
        }
      case metadata =>
        Attempt.Left {
          Failure(s"Could not parse SGOpenPorts from TrustedAdvisorResourceDetail with metadata $metadata", "Could not parse SGOpenPorts result", 500).attempt
        }
    }
  }

  def refreshSGOpenPorts(client: AwsClient[SupportAsyncClient])(implicit ec: ExecutionContext): Attempt[RefreshTrustedAdvisorCheckResponse] = {
    val delay = 3.seconds
    val checkId = AWS_SECURITY_GROUPS_PORTS_UNRESTRICTED_IDENTIFIER
    Retry.until(refreshTrustedAdvisorChecks(client, checkId), _.status.status == "success", s"Failed to refresh $checkId report", delay)
  }

}
