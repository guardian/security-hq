package aws.support

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.regions.Regions
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model._
import logic.DateUtils.fromISOString
import model._
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


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

  def client(taClients: Map[(String, Regions), AWSSupportAsync], awsAccount: AwsAccount): Attempt[AWSSupportAsync] = {
    val region = Regions.US_EAST_1
    Attempt.fromOption(taClients.get((awsAccount.id, region)), FailedAttempt(Failure(
      s"No AWS Trusted Advisor Client exists for ${awsAccount.id} and $region",
      s"Cannot find Trusted Advisor Client",
      500
    )))
  }

  // SHOW ALL TRUSTED ADVISOR CHECKS

  def getTrustedAdvisorChecks(client: AWSSupportAsync)(implicit ec: ExecutionContext): Attempt[List[TrustedAdvisorCheck]] = {
    val request = new DescribeTrustedAdvisorChecksRequest().withLanguage("en")
    handleAWSErrs()(awsToScala()(client.describeTrustedAdvisorChecksAsync)(request).map(parseTrustedAdvisorChecksResult))
  }

  def refreshTrustedAdvisorChecks(client: AWSSupportAsync, checkId: String)(implicit ec: ExecutionContext): Attempt[RefreshTrustedAdvisorCheckResult] = {
    val request = new RefreshTrustedAdvisorCheckRequest()
      .withCheckId(checkId)
    handleAWSErrs()(awsToScala()(client.refreshTrustedAdvisorCheckAsync)(request))
  }

  def parseTrustedAdvisorChecksResult(result: DescribeTrustedAdvisorChecksResult): List[TrustedAdvisorCheck] = {
    result.getChecks.asScala.toList.map { trustedAdvisorCheckResult =>
      TrustedAdvisorCheck(
        id = trustedAdvisorCheckResult.getId,
        name = trustedAdvisorCheckResult.getName,
        description = trustedAdvisorCheckResult.getDescription,
        category = trustedAdvisorCheckResult.getCategory
      )
    }
  }

  // GENERIC FUNCTIONALITY FOR DETAILED CHECK RESULTS

  def getTrustedAdvisorCheckDetails(client: AWSSupportAsync, checkId: String)(implicit ec: ExecutionContext): Attempt[DescribeTrustedAdvisorCheckResultResult] = {
    val request = new DescribeTrustedAdvisorCheckResultRequest()
      .withLanguage("en")
      .withCheckId(checkId)
    handleAWSErrs()(awsToScala()(client.describeTrustedAdvisorCheckResultAsync)(request))
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

  def parseTrustedAdvisorCheckResult[A <: TrustedAdvisorCheckDetails](parseDetails: TrustedAdvisorResourceDetail => Attempt[A], ec: ExecutionContext)(result: DescribeTrustedAdvisorCheckResultResult): Attempt[TrustedAdvisorDetailsResult[A]] = {
    implicit val executionContext: ExecutionContext = ec
    for {
      resources <- Attempt.traverse(result.getResult.getFlaggedResources.asScala.toList)(parseDetails)
    } yield TrustedAdvisorDetailsResult(
      checkId = result.getResult.getCheckId,
      status = result.getResult.getStatus,
      timestamp = fromISOString(result.getResult.getTimestamp),
      flaggedResources = sortSecurityFlags(resources),
      resourcesIgnored = result.getResult.getResourcesSummary.getResourcesIgnored,
      resourcesFlagged = result.getResult.getResourcesSummary.getResourcesFlagged,
      resourcesSuppressed = result.getResult.getResourcesSummary.getResourcesSuppressed
    )
  }
}
