package logic

import java.util.{Date, GregorianCalendar}

import com.amazonaws.services.inspector.model.{AssessmentRun, ListAssessmentRunsResult}
import logic.InspectorResults._
import model.InspectorAssessmentRun
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConverters._


class InspectorResultsTest extends FreeSpec with Matchers {
  val testAssessmentRun = InspectorAssessmentRun(
    "arn:run", "name", ("stack", "app", "stage"), "arn:template", "state", 1, Nil, Nil,
    DateTime.now(), DateTime.now(), DateTime.now(), DateTime.now(), true,
    Map("High" -> 0, "Medium" -> 0, "Low" -> 0, "Informational" -> 0)
  )

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

  "levelColour" - {
    "red if there is a high result" in {
      levelColour(Map("High" -> 1)) shouldEqual "red"
    }

    "yellow if there is a medium result" in {
      levelColour(Map("Medium" -> 1)) shouldEqual "yellow"
    }

    "blue if there is a low result" in {
      levelColour(Map("Low" -> 1)) shouldEqual "blue"
    }

    "grey if there is an info result" in {
      levelColour(Map("Informational" -> 1)) shouldEqual "grey"
    }

    "grey if there is are no results" in {
      levelColour(Map()) shouldEqual "grey"
    }

    "does not show high with 0 results" in {
      levelColour(Map("High" -> 0)) shouldEqual "grey"
    }

    "does not show medium with 0 results" in {
      levelColour(Map("Medium" -> 0)) shouldEqual "grey"
    }

    "does not show low with 0 results" in {
      levelColour(Map("Low" -> 0)) shouldEqual "grey"
    }

    "does not show info with 0 results" in {
      levelColour(Map("Informational" -> 0)) shouldEqual "grey"
    }

    "does not high with 0 results" in {
      levelColour(Map("High" -> 0)) shouldEqual "grey"
    }

    "high beats everything" in {
      levelColour(Map("High" -> 1, "Medium" -> 1, "Low" -> 1, "Informational" -> 1)) shouldEqual "red"
    }

    "medium beats low and info" in {
      levelColour(Map("High" -> 0, "Medium" -> 1, "Low" -> 1, "Informational" -> 1)) shouldEqual "yellow"
    }

    "info beats low" in {
      levelColour(Map("High" -> 0, "Medium" -> 0, "Low" -> 1, "Informational" -> 1)) shouldEqual "blue"
    }
  }

  "sortedFindings" - {
    "sorts keys correctly" in {
      val result = sortedFindings(Map("High" -> 0, "Medium" -> 0, "Low" -> 1, "Informational" -> 1))
      result shouldEqual List("High" -> 0, "Medium" -> 0, "Low" -> 1, "Informational" -> 1)
    }

    "puts unexpected keys at the end" in {
      val result = sortedFindings(Map("Strange" -> 0, "High" -> 0, "Medium" -> 0, "Low" -> 1, "Informational" -> 1))
      result shouldEqual List("High" -> 0, "Medium" -> 0, "Low" -> 1, "Informational" -> 1, "Strange" -> 0)
    }
  }

  "totalFindings" - {
    "returns total high findings" in {
      val results = List(
        arWithFindings(1, 0, 0, 0), arWithFindings(2, 0, 0, 0), arWithFindings(3, 0, 0, 0)
      )
      totalFindings("High", results) shouldEqual 6
    }

    "returns total medium findings" in {
      val results = List(
        arWithFindings(0, 1, 0, 0), arWithFindings(0, 2, 0, 0), arWithFindings(0, 3, 0, 0)
      )
      totalFindings("Medium", results) shouldEqual 6
    }

    "returns total low findings" in {
      val results = List(
        arWithFindings(0, 0, 1, 0), arWithFindings(0, 0, 2, 0), arWithFindings(0, 0, 3, 0)
      )
      totalFindings("Low", results) shouldEqual 6
    }

    "returns total info findings" in {
      val results = List(
        arWithFindings(0, 0, 0, 1), arWithFindings(0, 0, 0, 2), arWithFindings(0, 0, 0, 3)
      )
      totalFindings("Informational", results) shouldEqual 6
    }
  }

  "sortAccountResults" - {
    "puts failed results at the bottom" in {
      val results = List(
        () -> Left(()),
        () -> Right(List(arWithFindings(1, 0, 0, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(arWithFindings(1, 0, 0, 0))),
        () -> Left(())
      )
    }

    "sorts by Highs before Mediums" in {
      val results = List(
        () -> Right(List(arWithFindings(1, 2, 0, 0))),
        () -> Right(List(arWithFindings(2, 1, 0, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(arWithFindings(2, 1, 0, 0))),
        () -> Right(List(arWithFindings(1, 2, 0, 0)))
      )
    }

    "sorts by example correctly" in {
      val results = List(
        () -> Right(List(arWithFindings(2, 1, 1, 0))),
        () -> Right(List(arWithFindings(1, 1, 0, 0))),
        () -> Right(List(arWithFindings(3, 2, 0, 0))),
        () -> Right(List(arWithFindings(2, 2, 0, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(arWithFindings(3, 2, 0, 0))),
        () -> Right(List(arWithFindings(2, 2, 0, 0))),
        () -> Right(List(arWithFindings(2, 1, 1, 0))),
        () -> Right(List(arWithFindings(1, 1, 0, 0)))
      )
    }
  }

  def arWithFindings(high: Int, medium: Int, low: Int, info: Int): InspectorAssessmentRun = {
    testAssessmentRun.copy(
      findingCounts = Map(
        "High" -> high,
        "Medium" -> medium,
        "Low" -> low,
        "Informational" -> info
      )
    )
  }
}
