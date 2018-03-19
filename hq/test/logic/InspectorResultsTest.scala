package logic

import java.util.{Date, GregorianCalendar}

import com.amazonaws.services.inspector.model.{AssessmentRun, ListAssessmentRunsResult}
import logic.InspectorResults._
import model.InspectorAssessmentRun
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers, OptionValues}

import scala.collection.JavaConverters._


class InspectorResultsTest extends FreeSpec with Matchers with OptionValues {
  val assessmentRun = InspectorAssessmentRun(
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
    val assessmentRun = new AssessmentRun()
      .withArn("arn:123")
      .withName("AWSInspection--stack--app--stage--1520873440000")
      .withAssessmentTemplateArn("arn:template")
      .withState("COMPLETED")
      .withDurationInSeconds(123)
      .withRulesPackageArns("arn:rules1", "arn:rules2")
      .withUserAttributesForFindings()
      .withCreatedAt(new Date())
      .withStartedAt(new Date())
      .withCompletedAt(new GregorianCalendar(2018, 2, 13, 0, 0, 0).getTime)
      .withStartedAt(new Date())
      .withDataCollected(true)
      .withFindingCounts(Map("low" -> new Integer(1)).asJava)

    "correctly extracts some of the important fields" in {
      parseCompletedAssessmentRun(assessmentRun).value should have(
        'arn ("arn:123"),
        'name ("AWSInspection--stack--app--stage--1520873440000"),
        'appId ("stack", "app", "stage"),
        'rulesPackageArns (List("arn:rules1", "arn:rules2")),
        'completedAt (new DateTime(2018, 3 , 13, 0, 0, 0)),
        'findingCounts (Map("low" -> 1))
      )
    }

    "fails to parse an incomplete assessment run" in {
      val runningAssessmentRun = assessmentRun
        .withState("COLLECTING_DATA")
        .withDataCollected(false)
      parseCompletedAssessmentRun(runningAssessmentRun) shouldBe None
    }

    "fails to parse an assessment run that does not match the format used by our automated tool" in {
      val runningAssessmentRun = assessmentRun
        .withName("Not our tool")
      parseCompletedAssessmentRun(runningAssessmentRun) shouldBe None
    }
  }

  "appId" - {
    "parses a valid lambda inspector name" in {
      appId("AWSInspection--stack--app--stage--1520873440000").value shouldEqual ("stack", "app", "stage")
    }

    "parses a valid lambda inspector name with funny chars in the tags" in {
      val result = appId("AWSInspection--stack-with-hyphens--app_with_underscores--stage.with.dots--1520873440000").value
      result shouldEqual ("stack-with-hyphens", "app_with_underscores", "stage.with.dots")
    }

    "returns None if it does not match the expected format" in {
      appId("something-else") shouldBe None
    }
  }

  "relevantRuns" - {
    "takes latest run for an app id" in {
      val latest = assessmentRun
        .withAppId("stack", "app", "stage")
        .withCompletionAt(DateTime.now())
      val older = latest
        .withCompletionAt(DateTime.now().minusDays(1))

      relevantRuns(List(latest, older)) shouldEqual List(latest)
    }

    "takes latest run for multiple app ids" in {
      val latest1 = assessmentRun
        .withAppId("stack1", "app1", "stage1")
        .withCompletionAt(DateTime.now())
      val older1 = latest1
        .withCompletionAt(DateTime.now().minusDays(1))
      val latest2 = assessmentRun
        .withAppId("stack2", "app2", "stage2")
        .withCompletionAt(DateTime.now())
      val older2 = latest2
        .withCompletionAt(DateTime.now().minusDays(2))

      relevantRuns(List(latest1, older1, latest2, older2)) should contain only(latest1, latest2)
    }

    "sorts on high before everything" in {
      val app1 = assessmentRun
        .withAppId("stack1", "app1", "stage1")
        .withFindings(2, 0, 0, 0)
      val app2 = assessmentRun
        .withAppId("stack2", "app2", "stage2")
        .withFindings(1, 10, 10, 10)

      relevantRuns(List(app2, app1)) shouldEqual List(app1, app2)
    }

    "sorts on medium before low and info" in {
      val app1 = assessmentRun
        .withAppId("stack1", "app1", "stage1")
        .withFindings(0, 2, 0, 0)
      val app2 = assessmentRun
        .withAppId("stack2", "app2", "stage2")
        .withFindings(0, 1, 10, 10)

      relevantRuns(List(app2, app1)) shouldEqual List(app1, app2)
    }

    "sorts on low before info" in {
      val app1 = assessmentRun
        .withAppId("stack1", "app1", "stage1")
        .withFindings(0, 0, 2, 0)
      val app2 = assessmentRun
        .withAppId("stack2", "app2", "stage2")
        .withFindings(0, 0, 1, 10)

      relevantRuns(List(app2, app1)) shouldEqual List(app1, app2)
    }
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

  "levelFindings" - {
    "returns total high findings" in {
      val results = List(
        assessmentRun.withFindings(1, 0, 0, 0), assessmentRun.withFindings(2, 0, 0, 0), assessmentRun.withFindings(3, 0, 0, 0)
      )
      levelFindings("High", results) shouldEqual 6
    }

    "returns total medium findings" in {
      val results = List(
        assessmentRun.withFindings(0, 1, 0, 0), assessmentRun.withFindings(0, 2, 0, 0), assessmentRun.withFindings(0, 3, 0, 0)
      )
      levelFindings("Medium", results) shouldEqual 6
    }

    "returns total low findings" in {
      val results = List(
        assessmentRun.withFindings(0, 0, 1, 0), assessmentRun.withFindings(0, 0, 2, 0), assessmentRun.withFindings(0, 0, 3, 0)
      )
      levelFindings("Low", results) shouldEqual 6
    }

    "returns total info findings" in {
      val results = List(
        assessmentRun.withFindings(0, 0, 0, 1), assessmentRun.withFindings(0, 0, 0, 2), assessmentRun.withFindings(0, 0, 0, 3)
      )
      levelFindings("Informational", results) shouldEqual 6
    }
  }

  "totalFindings" - {
    "returns 0 for empty runs list" in {
      totalFindings(Nil) shouldEqual 0
    }

    "returns sum for a list of runs" in {
      val results = List(
        assessmentRun.withFindings(1, 0, 0, 0), assessmentRun.withFindings(0, 1, 0, 0), assessmentRun.withFindings(0, 0, 1, 0), assessmentRun.withFindings(0, 0, 0, 1)
      )
      totalFindings(results) shouldEqual 4
    }
  }

  "sortAccountResults" - {
    "puts failed results at the bottom" in {
      val results = List(
        () -> Left(()),
        () -> Right(List(assessmentRun.withFindings(1, 0, 0, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(assessmentRun.withFindings(1, 0, 0, 0))),
        () -> Left(())
      )
    }

    "sorts by Highs before everything" in {
      val results = List(
        () -> Right(List(assessmentRun.withFindings(1, 10, 10, 10))),
        () -> Right(List(assessmentRun.withFindings(2, 0, 0, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(assessmentRun.withFindings(2, 0, 0, 0))),
        () -> Right(List(assessmentRun.withFindings(1, 10, 10, 10)))
      )
    }

    "sorts by Mediums before Lows and Infos" in {
      val results = List(
        () -> Right(List(assessmentRun.withFindings(0, 1, 10, 10))),
        () -> Right(List(assessmentRun.withFindings(0, 2, 0, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(assessmentRun.withFindings(0, 2, 0, 0))),
        () -> Right(List(assessmentRun.withFindings(0, 1, 10, 10)))
      )
    }

    "sorts by Lows before Infos" in {
      val results = List(
        () -> Right(List(assessmentRun.withFindings(0, 0, 1, 10))),
        () -> Right(List(assessmentRun.withFindings(0, 0, 2, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(assessmentRun.withFindings(0, 0, 2, 0))),
        () -> Right(List(assessmentRun.withFindings(0, 0, 1, 10)))
      )
    }

    "sorts by example correctly" in {
      val results = List(
        () -> Right(List(assessmentRun.withFindings(2, 1, 1, 0))),
        () -> Right(List(assessmentRun.withFindings(1, 1, 0, 0))),
        () -> Right(List(assessmentRun.withFindings(3, 2, 0, 0))),
        () -> Right(List(assessmentRun.withFindings(2, 2, 0, 0)))
      )
      sortAccountResults(results) shouldEqual List(
        () -> Right(List(assessmentRun.withFindings(3, 2, 0, 0))),
        () -> Right(List(assessmentRun.withFindings(2, 2, 0, 0))),
        () -> Right(List(assessmentRun.withFindings(2, 1, 1, 0))),
        () -> Right(List(assessmentRun.withFindings(1, 1, 0, 0)))
      )
    }
  }

  implicit class TestInspectorAssessmentRun(iar: InspectorAssessmentRun) {
    def withFindings(high: Int, medium: Int, low: Int, info: Int): InspectorAssessmentRun = {
      iar.copy(
        findingCounts = Map(
          "High" -> high,
          "Medium" -> medium,
          "Low" -> low,
          "Informational" -> info
        )
      )
    }

    def withCompletionAt(completionTime: DateTime): InspectorAssessmentRun = {
      iar.copy(completedAt = completionTime)
    }

    def withAppId(stack: String, app: String, stage: String): InspectorAssessmentRun = {
      iar.copy(appId = (stack, app, stage))
    }
  }
}
