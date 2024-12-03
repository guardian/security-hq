package aws.support

import aws.AwsAsyncHandler.{asScala, handleAWSErrs}
import aws.{AwsClient, AwsClients}

import logic.DateUtils.fromISOString
import model._
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.support.SupportAsyncClient
import software.amazon.awssdk.services.support.model._


object TrustedAdvisor {
  val portPriorityMap =
    Seq(
      "FTP" -> Set(20, 21),
      "Postgres" -> Set(5432),
      "MySQL" -> Set(3306),
      "Redshift" -> Set(5439),
      "MongoDB" -> Set(27017, 27018, 27019),
      "Redis" -> Set(6379, 6380),
      "MSQL" -> Set(4333),
      "Oracle DB" -> Set(5500),
      "SQL Server" -> Set(1433, 1434),
      "RDP" -> Set(3389),
      "Play FW" -> Set(9000),
      "Spark" -> Set(7077, 4040, 4041, 4042),
      "EMR" -> Set(8890),
      "Spark Web" -> Set(8080),
      "Kibana" -> Set(5601),
      "Elastic Search" -> Set(9200, 9300),
      "SSH" -> Set(22)
    )

  val indexedPortMap = portPriorityMap.zipWithIndex

  val alertLevelMapping = Map("Red" -> 0, "Yellow" -> 1, "Green" -> 2)

  // SHOW ALL TRUSTED ADVISOR CHECKS

  def getTrustedAdvisorChecks(client: AwsClient[SupportAsyncClient])(implicit ec: ExecutionContext): Attempt[List[TrustedAdvisorCheck]] = {
    val request = DescribeTrustedAdvisorChecksRequest.builder.language("en").build()
    handleAWSErrs(client)(asScala(client.client.describeTrustedAdvisorChecks(request))).map(parseTrustedAdvisorChecksResponse)
  }

  def refreshTrustedAdvisorChecks(client: AwsClient[SupportAsyncClient], checkId: String)(implicit ec: ExecutionContext): Attempt[RefreshTrustedAdvisorCheckResponse] = {
    val request = RefreshTrustedAdvisorCheckRequest.builder.checkId(checkId).build()
    handleAWSErrs(client)(asScala(client.client.refreshTrustedAdvisorCheck(request)))
  }

  def parseTrustedAdvisorChecksResponse(result: DescribeTrustedAdvisorChecksResponse): List[TrustedAdvisorCheck] = {
    result.checks.asScala.toList.map { trustedAdvisorCheckResult =>
      TrustedAdvisorCheck(
        id = trustedAdvisorCheckResult.id,
        name = trustedAdvisorCheckResult.name,
        description = trustedAdvisorCheckResult.description,
        category = trustedAdvisorCheckResult.category
      )
    }
  }

  // GENERIC FUNCTIONALITY FOR DETAILED CHECK RESULTS

  def getTrustedAdvisorCheckDetails(client: AwsClient[SupportAsyncClient], checkId: String)(implicit ec: ExecutionContext): Attempt[DescribeTrustedAdvisorCheckResultResponse] = {
    val request = DescribeTrustedAdvisorCheckResultRequest.builder
      .language("en")
      .checkId(checkId)
      .build()
    handleAWSErrs(client)(asScala(client.client.describeTrustedAdvisorCheckResult(request)))
  }

  private[support] def findPortPriorityIndex(port: String) = {
    Option(port).flatMap { case p if p.trim.nonEmpty =>
      val allPorts = p.trim.split("-").map(_.toInt).toList match {
        case head :: tail :: Nil => (head to tail).toSet
        case head :: Nil => Set(head)
        case _ => Set.empty[Int]
      }
      indexedPortMap.collectFirst {
        case ((_, seq), idx) if allPorts.diff(seq) != allPorts  => idx
      }
    }
  }

  private[support] def sortSecurityFlags[A <: TrustedAdvisorCheckDetails](list: List[A]): List[A] = {

    list.sortWith {
      case (a: RDSSGsDetail, b: RDSSGsDetail) =>
        alertLevelMapping.getOrElse(a.alertLevel, 1) < alertLevelMapping.getOrElse(b.alertLevel, 1)
      case (a: SGOpenPortsDetail, b: SGOpenPortsDetail) =>
        if (a.alertLevel == b.alertLevel) {
          findPortPriorityIndex(a.port).getOrElse(999) < findPortPriorityIndex(b.port).getOrElse(999)
        } else
          alertLevelMapping.getOrElse(a.alertLevel, 2) < alertLevelMapping.getOrElse(b.alertLevel, 2)
      case (_, _) => false
    }
  }

  def parseTrustedAdvisorCheckResult[A <: TrustedAdvisorCheckDetails](parseDetails: TrustedAdvisorResourceDetail => Attempt[A], ec: ExecutionContext)(response: DescribeTrustedAdvisorCheckResultResponse): Attempt[TrustedAdvisorDetailsResult[A]] = {
    implicit val executionContext: ExecutionContext = ec
    val result = response.result
    for {
      resources <- Attempt.traverse(result.flaggedResources.asScala.toList)(parseDetails)
    } yield TrustedAdvisorDetailsResult(
      checkId = result.checkId,
      status = result.status,
      timestamp = fromISOString(result.timestamp),
      flaggedResources = sortSecurityFlags(resources),
      resourcesIgnored = result.resourcesSummary.resourcesIgnored,
      resourcesFlagged = result.resourcesSummary.resourcesFlagged,
      resourcesSuppressed = result.resourcesSummary.resourcesSuppressed
    )
  }
}
