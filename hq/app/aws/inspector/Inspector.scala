package aws.inspector

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.inspector.model._
import com.amazonaws.services.inspector.{AmazonInspectorAsync, AmazonInspectorAsyncClientBuilder}
import logic.InspectorResults
import logic.InspectorResults._
import model.{AwsAccount, InspectorAssessmentRun}
import org.joda.time.DateTime
import utils.attempt.Attempt

import collection.JavaConverters._
import scala.concurrent.ExecutionContext


object Inspector {
  def client(auth: AWSCredentialsProviderChain, region: Regions): AmazonInspectorAsync = {
    AmazonInspectorAsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(region)
      .build()
  }

  def client(awsAccount: AwsAccount, region: Regions): AmazonInspectorAsync = {
    val auth = AWS.credentialsProvider(awsAccount)
    client(auth, region)
  }

  def listInspectorRuns(client: AmazonInspectorAsync)(implicit ec: ExecutionContext): Attempt[List[String]] = {
    val request = new ListAssessmentRunsRequest()
    handleAWSErrs(awsToScala(client.listAssessmentRunsAsync)(request)).map(parseListAssessmentRunsResult)
  }

  def describeInspectorRuns(assessmentRunArns: List[String], client: AmazonInspectorAsync)(implicit ec: ExecutionContext): Attempt[List[InspectorAssessmentRun]] = {
    if (assessmentRunArns.isEmpty) {
      // empty assessmentRunArns throws an exception, so we handle that here
      Attempt.Right(Nil)
    } else {
      val request = new DescribeAssessmentRunsRequest()
        .withAssessmentRunArns(assessmentRunArns.asJava)
      handleAWSErrs(awsToScala(client.describeAssessmentRunsAsync)(request)).map(parseDescribeAssessmentRunsResult)
    }
  }

  def inspectorRuns(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[InspectorAssessmentRun]] = {
    val region = Regions.EU_WEST_1  // for now
    val inspectorClient = client(account, region)
    for {
      inspectorRunArns <- Inspector.listInspectorRuns(inspectorClient)
      assessmentRuns <- Inspector.describeInspectorRuns(inspectorRunArns, inspectorClient)
      processedAssessmentRuns = InspectorResults.relevantRuns(assessmentRuns)
    } yield processedAssessmentRuns
  }
}
