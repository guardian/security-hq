package logic

import org.scalatest.{EitherValues, Matchers, WordSpec}
import utils.attempt.{Attempt, AttemptValues}
import scala.concurrent.ExecutionContext.Implicits.global

class RetryTest extends WordSpec with Matchers with AttemptValues {
  "looped attempt" should {
    val failMessage = "Retry failed"
    val InProgress = "INPROGRESS"
    val Complete = "COMPLETE"
    val Started = "STARTED"
    val maxAttempt = 10
    val predicateF = { status: String => status == Complete }

    "retry with inprogress result and check max attempt" in {
      var attempts = 0

      def testBody = {
        val body = Attempt.Right(InProgress)
        attempts = attempts + 1
        body
      }

      Retry.until(testBody, predicateF, failMessage).isFailedAttempt shouldBe true
      attempts shouldBe maxAttempt
    }

    "retry with complete result" in {
      val reportStatus = Complete
      val body = Attempt.Right(reportStatus)
      Retry.until(body, predicateF, failMessage).value shouldBe Complete
    }

    "retry first with started then inprogress then complete" in {
      val reportStatuses = Seq(Started, Started, InProgress, InProgress, Complete)
      var attempts = 0

      def testBody = {
        val body = Attempt.Right(reportStatuses(attempts))
        attempts = attempts + 1
        body
      }

      Retry.until(testBody, predicateF, failMessage).value shouldBe Complete
      attempts shouldBe 5
    }
  }
}
