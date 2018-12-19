package aws.inspector

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.{AwsClient, AwsClients}
import com.amazonaws.regions.Regions
import com.amazonaws.services.inspector.AmazonInspectorAsync
import com.amazonaws.services.inspector.model._
import logic.InspectorResults
import logic.InspectorResults._
import model.{AwsAccount, InspectorAssessmentRun}
import utils.attempt.{Attempt, FailedAttempt}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object Inspector {

  def listInspectorRuns(client: AwsClient[AmazonInspectorAsync])(implicit ec: ExecutionContext): Attempt[List[String]] = {
    val request = new ListAssessmentRunsRequest()
    handleAWSErrs(client)(awsToScala(client)(_.listAssessmentRunsAsync)(request)).map(parseListAssessmentRunsResult)
  }

  def describeInspectorRuns(assessmentRunArns: List[String], client: AwsClient[AmazonInspectorAsync])(implicit ec: ExecutionContext): Attempt[List[InspectorAssessmentRun]] = {
    if (assessmentRunArns.isEmpty) {
      // empty assessmentRunArns throws an exception, so we handle that here
      Attempt.Right(Nil)
    } else {
      val request = new DescribeAssessmentRunsRequest()
        .withAssessmentRunArns(assessmentRunArns.asJava)
      handleAWSErrs(client)(awsToScala(client)(_.describeAssessmentRunsAsync)(request)).map(parseDescribeAssessmentRunsResult)
    }
  }

  def allInspectorRuns(accounts: List[AwsAccount], inspectorClients: AwsClients[AmazonInspectorAsync])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[InspectorAssessmentRun]])]] = {
    Attempt.labelledTraverseWithFailures(accounts)(Inspector.inspectorRuns(inspectorClients))
  }

  def inspectorRuns(inspectorClients: AwsClients[AmazonInspectorAsync])(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[InspectorAssessmentRun]] = {
    val region = Regions.EU_WEST_1  // we only automatically run inspections in Ireland

    for {
      inspectorClient <- inspectorClients.get(account, region)
      inspectorRunArns <- Inspector.listInspectorRuns(inspectorClient)
      assessmentRuns <- Inspector.describeInspectorRuns(inspectorRunArns, inspectorClient)
      processedAssessmentRuns = InspectorResults.relevantRuns(assessmentRuns)
    } yield processedAssessmentRuns
  }
}
