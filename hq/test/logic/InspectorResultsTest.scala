package logic

import java.util.{Date, GregorianCalendar}

import com.amazonaws.services.inspector.model.{AssessmentRun, ListAssessmentRunsResult}
import logic.InspectorResults._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConverters._


class InspectorResultsTest extends FreeSpec with Matchers {

  "parseListAssessmentRunsResult" - {
    "returns the ARNs in the result" in {
      val result = new ListAssessmentRunsResult().withAssessmentRunArns("arn:123", "arn:456")
      parseListAssessmentRunsResult(result) shouldEqual List("arn:123", "arn:456")
    }
  }

  "parseAssessmentRun" - {
    "correctly extracts some of the important fields" in {
      val assessmentRun = new AssessmentRun()
        .withArn("arn:123")
        .withName("AWSInspection-stack-app-stage-1520873440000")
        .withAssessmentTemplateArn("arn:template")
        .withState("state")
        .withDurationInSeconds(123)
        .withRulesPackageArns("arn:rules1", "arn:rules2")
        .withUserAttributesForFindings()
        .withCreatedAt(new Date())
        .withStartedAt(new Date())
        .withCompletedAt(new GregorianCalendar(2018, 2, 13, 0, 0, 0).getTime)
        .withStartedAt(new Date())
        .withDataCollected(true)
        .withFindingCounts(Map("low" -> new Integer(1)).asJava)
      parseAssessmentRun(assessmentRun) should have(
        'arn ("arn:123"),
        'name ("AWSInspection-stack-app-stage-1520873440000"),
        'appId ("stack", "app", "stage"),
        'rulesPackageArns (List("arn:rules1", "arn:rules2")),
        'completedAt (new DateTime(2018, 3 , 13, 0, 0, 0)),
        'findingCounts (Map("low" -> 1))
      )
    }
  }

  "appId" - {
    "parses a valid lambda inspector name" in {
      appId("AWSInspection-stack-app-stage-1520873440000") shouldEqual ("stack", "app", "stage")
    }

    "parses a valid lambda inspector name with funny chars in the tags" in {
      val result = appId("AWSInspection-stack-with-hyphens-app_with_underscores-stage.with.dots-1520873440000")
      result shouldEqual ("stack-with-hyphens", "app_with_underscores", "stage.with.dots")
    }

    "uses entire name as app if it does not match the expected format" in {
      appId("something-else") shouldEqual ("", "something-else", "")
    }
  }

  "relevantRuns" - {
    "takes latest run for an app id" ignore {}

    "takes latest run for multiple app ids" ignore {}

    "sorts multiple app ids by the total number of findings in latest runs" ignore {}

    "non-lambda runs are included with name as app, and sorted accordingly" ignore {}
  }
}
