package aws.support

import aws.Auth
import aws.AwsAsyncHandler.awsToScala
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.support.model._
import com.amazonaws.services.support.{AWSSupportAsync, AWSSupportAsyncClientBuilder}
import model._
import logic.DateUtils.fromISOString

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object TrustedAdvisor {
  def client(auth: AWSCredentialsProviderChain): AWSSupportAsync = {
    AWSSupportAsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(Regions.US_EAST_1) // Support's a global service, needs to be set to US_EAST_1
      .build()
  }

  def client(awsAccount: AwsAccount): AWSSupportAsync = {
    val auth = Auth.credentialsProvider(awsAccount)
    client(auth)
  }

  // SHOW ALL TRUSTED ADVISOR CHECKS

  def getTrustedAdvisorChecks(client: AWSSupportAsync)(implicit ec: ExecutionContext): Future[List[TrustedAdvisorCheck]] = {
    val request = new DescribeTrustedAdvisorChecksRequest().withLanguage("en")
    awsToScala(client.describeTrustedAdvisorChecksAsync)(request).map(parseTrustedAdvisorChecksResult)
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

  def getTrustedAdvisorCheckDetails(client: AWSSupportAsync, checkId: String)(implicit ec: ExecutionContext): Future[DescribeTrustedAdvisorCheckResultResult] = {
    val request = new DescribeTrustedAdvisorCheckResultRequest()
      .withLanguage("en")
      .withCheckId(checkId)
    awsToScala(client.describeTrustedAdvisorCheckResultAsync)(request)
  }

  def parseTrustedAdvisorCheckResult[A <: TrustedAdvisorCheckDetails](parseDetails: TrustedAdvisorResourceDetail => A)(result: DescribeTrustedAdvisorCheckResultResult): TrustedAdvisorDetailsResult[A] = {
    TrustedAdvisorDetailsResult(
      checkId = result.getResult.getCheckId,
      status = result.getResult.getStatus,
      timestamp = fromISOString(result.getResult.getTimestamp),
      flaggedResources = result.getResult.getFlaggedResources.asScala.toList.map(parseDetails),
      resourcesIgnored = result.getResult.getResourcesSummary.getResourcesIgnored,
      resourcesFlagged = result.getResult.getResourcesSummary.getResourcesFlagged,
      resourcesSuppressed = result.getResult.getResourcesSummary.getResourcesSuppressed
    )
  }
}
