package aws.inspector

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.regions.Regions
import com.amazonaws.services.inspector.AmazonInspectorAsync
import com.amazonaws.services.inspector.model._
import logic.InspectorResults
import logic.InspectorResults._
import model.{AwsAccount, InspectorAssessmentRun}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object Inspector {

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

  def allInspectorRuns(accounts: List[AwsAccount], inspectorClients: Map[(String, Regions), AmazonInspectorAsync])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[InspectorAssessmentRun]])]] = {
    Attempt.labelledTraverseWithFailures(accounts)(Inspector.inspectorRuns(inspectorClients))
  }

  def inspectorRuns(inspectorClients: Map[(String, Regions), AmazonInspectorAsync])(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[InspectorAssessmentRun]] = {
    val region = Regions.EU_WEST_1  // we only automatically run inspections in Ireland

    for {
      inspectorClient <- Attempt.fromOption(inspectorClients.get((account.id, region)), FailedAttempt(Failure(
          s"No AWS Inspector Client exists for ${account.id} and $region",
          s"Cannot find Inspector Client",
          500
      )))
      inspectorRunArns <- Inspector.listInspectorRuns(inspectorClient)
      assessmentRuns <- Inspector.describeInspectorRuns(inspectorRunArns, inspectorClient)
      processedAssessmentRuns = InspectorResults.relevantRuns(assessmentRuns)
    } yield processedAssessmentRuns
  }
}
