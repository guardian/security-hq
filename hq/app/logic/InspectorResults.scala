package logic

import com.amazonaws.services.inspector.model.{AssessmentRun, DescribeAssessmentRunsResult, _}
import model.InspectorAssessmentRun
import org.joda.time.DateTime

import scala.collection.JavaConverters._


object InspectorResults {
  private val tagMatch = "[\\w\\-_\\.]"
  val RunNameMatch = s"AWSInspection-($tagMatch+)-($tagMatch+)-($tagMatch+)-[\\d]+".r

  def appId(assessmentRunName: String): (String, String, String) = {
    assessmentRunName match {
      case RunNameMatch(stack, app, stage) =>
        (stack, app, stage)
      case _ =>
        ("", assessmentRunName, "")
    }
  }

  /**
    * Take latest results for each App ID
    *
    * Sorts results by findings. TODO: prioritize high importance findings when keys are known.
    */
  def relevantRuns(runs: List[InspectorAssessmentRun]): List[((String, String, String), InspectorAssessmentRun)] = {
    val result = runs.groupBy(_.appId).mapValues(_.maxBy(_.completedAt.getMillis))
    result.toList.sortBy { case (_, assessmentRun) =>
      // descending
      0 - assessmentRun.findingCounts.values.sum
    }
  }

  def parseListAssessmentRunsResult(result: ListAssessmentRunsResult): List[String] = {
    result.getAssessmentRunArns.asScala.toList
  }

  def parseDescribeAssessmentRunsResult(result: DescribeAssessmentRunsResult): List[InspectorAssessmentRun] = {
    result.getAssessmentRuns.asScala.toList.map(parseAssessmentRun)
  }

  private[logic] def parseAssessmentRun(assessmentRun: AssessmentRun): InspectorAssessmentRun = {
    InspectorAssessmentRun(
      arn = assessmentRun.getArn,
      name = assessmentRun.getName,
      appId = InspectorResults.appId(assessmentRun.getName),
      assessmentTemplateArn = assessmentRun.getAssessmentTemplateArn,
      state = assessmentRun.getState,
      durationInSeconds = assessmentRun.getDurationInSeconds,
      rulesPackageArns = assessmentRun.getRulesPackageArns.asScala.toList,
      userAttributesForFindings = assessmentRun.getUserAttributesForFindings.asScala.toList.map(attr => (attr.getKey, attr.getValue)),
      createdAt = new DateTime(assessmentRun.getCreatedAt),
      startedAt = new DateTime(assessmentRun.getStartedAt),
      completedAt = new DateTime(assessmentRun.getCompletedAt),
      stateChangedAt = new DateTime(assessmentRun.getStateChangedAt),
      dataCollected = assessmentRun.getDataCollected,
      findingCounts = assessmentRun.getFindingCounts.asScala.toMap.mapValues(_.toInt)
    )
  }
}
