package logic

import com.amazonaws.services.inspector.model.{AssessmentRun, DescribeAssessmentRunsResult, _}
import model.InspectorAssessmentRun
import org.joda.time.DateTime
import utils.attempt.FailedAttempt

import scala.collection.JavaConverters._


object InspectorResults {
  private val tagMatch = "[\\w\\-_\\.]"
  val RunNameMatch = s"AWSInspection--($tagMatch+)--($tagMatch+)--($tagMatch+)-[\\d]+".r

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
    * Sorts results descending by findings (first by High, then Medium, Low, Informational).
    * Breaks remaining ties on the total number of results.
    */
  def relevantRuns(runs: List[InspectorAssessmentRun]): List[InspectorAssessmentRun] = {
    val result = runs.groupBy(_.appId).mapValues(_.maxBy(_.completedAt.getMillis)).values
    result.toList.sortBy { assessmentRun =>
      // descending
      ( assessmentRun.findingCounts.get("High").map(_ * -1)
      , assessmentRun.findingCounts.get("Medium").map(_ * -1)
      , assessmentRun.findingCounts.get("Low").map(_ * -1)
      , assessmentRun.findingCounts.get("Informational").map(_ * -1)
      , assessmentRun.findingCounts.values.sum * -1
      )
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

  def sortAccountResults[A, B](accountResults: List[(A, scala.Either[B, List[InspectorAssessmentRun]])]): List[(A, scala.Either[B, List[InspectorAssessmentRun]])] = {
    accountResults.sortBy {
      case (_, Right(assessmentRuns)) =>
        ( 0 - levelFindings("High", assessmentRuns)
        , 0 - levelFindings("Medium", assessmentRuns)
        , 0 - levelFindings("Low", assessmentRuns)
        , 0 - levelFindings("Info", assessmentRuns)
        )
      case (_, Left(_)) =>
        (1, 1, 1, 1)
    }
  }

  def levelColour(assessmentFindings: Map[String, Int]): String = {
    val high = assessmentFindings.get("High").filter(_ > 0).map(_ => "red")
    val medium = assessmentFindings.get("Medium").filter(_ > 0).map(_ => "yellow")
    val low = assessmentFindings.get("Low").filter(_ > 0).map(_ => "blue")
    val info = assessmentFindings.get("Informational").filter(_ > 0).map(_ => "grey")

    high.orElse(medium).orElse(low).orElse(info).getOrElse("grey")
  }

  def sortedFindings(findings: Map[String, Int]): List[(String, Int)] = {
    List(
      findings.get("High").map("High" -> _),
      findings.get("Medium").map("Medium" -> _),
      findings.get("Low").map("Low" -> _),
      findings.get("Informational").map("Informational" -> _)
    ).flatten ++ (findings - "High" - "Medium" - "Low" - "Informational").toList
  }

  def levelFindings(level: String, assessmentRuns: List[InspectorAssessmentRun]): Int = {
    assessmentRuns.map(_.findingCounts.getOrElse(level, 0)).sum
  }

  def totalFindings(assessmentRuns: List[InspectorAssessmentRun]): Int = {
    assessmentRuns.flatMap(_.findingCounts.values).sum
  }
}
