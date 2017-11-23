package aws.support

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.support.model._
import com.amazonaws.services.support.{AWSSupportAsync, AWSSupportAsyncClientBuilder}
import model._
import logic.DateUtils.fromISOString
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object TrustedAdvisor {
  val portPriorityMap = Map("27017" -> 0, "27018" -> 0, "27019" -> 0, "6379" -> 0, "6380" -> 0,  "9000" -> 1, "7077" -> 1, "4040" -> 1, "8890" -> 1, "4041" -> 1, "4042" -> 1, "8080" -> 1, "22" -> 9)
  val alertLevelMapping = Map("Red" -> 0, "Yellow" -> 1, "Green" -> 2)

  def client(auth: AWSCredentialsProviderChain): AWSSupportAsync = {
    AWSSupportAsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(Regions.US_EAST_1) // Support's a global service, needs to be set to US_EAST_1
      .build()
  }

  def client(awsAccount: AwsAccount): AWSSupportAsync = {
    val auth = AWS.credentialsProvider(awsAccount)
    client(auth)
  }

  // SHOW ALL TRUSTED ADVISOR CHECKS

  def getTrustedAdvisorChecks(client: AWSSupportAsync)(implicit ec: ExecutionContext): Attempt[List[TrustedAdvisorCheck]] = {
    val request = new DescribeTrustedAdvisorChecksRequest().withLanguage("en")
    handleAWSErrs(awsToScala(client.describeTrustedAdvisorChecksAsync)(request).map(parseTrustedAdvisorChecksResult))
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
    handleAWSErrs(awsToScala(client.describeTrustedAdvisorCheckResultAsync)(request))
  }

  private[support] def sortSecurityFlags[A <: TrustedAdvisorCheckDetails](list: List[A]): List[A] = {
    //    AWS Alert mappings
    //    Green: Access to port 80, 25, 443, or 465 is unrestricted.
    //    Red: Access to port 20, 21, 1433, 1434, 3306, 3389, 4333, 5432, or 5500 is unrestricted.
    //    Yellow: Access to any other port is unrestricted.
    //
    //    FTP : 21
    //    SSH : 22
    //    SQL Server : 1433 x
    //    PostgreSQL : 5432 x
    //    MySQL : 3306 x
    //    Amazon EMR Web : 8890 y
    //    Play framework : 9000 y
    //    MongoDB : 27017, 27018, 27019 y
    //    Redis : 6379, 6380  y
    //    Spark web : 8080 y
    //    Spark master: 7077 y
    //    Spark UI : 4040, 4041, 4042 y
    list.sortWith {
      case (a: RDSSGsDetail, b: RDSSGsDetail) =>
        alertLevelMapping.getOrElse(a.alertLevel, 1) < alertLevelMapping.getOrElse(b.alertLevel, 1)
      case (a: SGOpenPortsDetail, b: SGOpenPortsDetail) =>
        if (a.alertLevel == b.alertLevel) {
          portPriorityMap.getOrElse(a.port, 2) < portPriorityMap.getOrElse(b.port, 2)
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
