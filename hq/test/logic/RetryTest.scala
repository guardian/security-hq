package logic

import utils.attempt.{Attempt, AttemptValues}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RetryTest extends AnyWordSpec with Matchers with AttemptValues {
  "looped attempt" should {
    val failMessage = "Retry failed"
    val InProgress = "INPROGRESS"
    val Complete = "COMPLETE"
    val Started = "STARTED"
    val maxAttempt = 10
    val predicateF = { status: String => status == Complete }
    val testDelay = 10.millisecond

    "retry with inprogress result and check max attempt" in {
      var attempts = 0

      def testBody = {
        val body = Attempt.Right(InProgress)
        attempts = attempts + 1
        body
      }

      Retry.until(testBody, predicateF, failMessage, Duration.Zero).isFailedAttempt() shouldBe true
      attempts shouldBe maxAttempt
    }

    "retry with complete result" in {
      val reportStatus = Complete
      val body = Attempt.Right(reportStatus)
      Retry.until(body, predicateF, failMessage, Duration.Zero).value() shouldBe Complete
    }

    "retry first with started then inprogress then complete" in {
      val reportStatuses = Seq(Started, Started, InProgress, InProgress, Complete)
      var attempts = 0

      def testBody = {
        val body = Attempt.Right(reportStatuses(attempts))
        attempts = attempts + 1
        body
      }

      Retry.until(testBody, predicateF, failMessage, Duration.Zero).value() shouldBe Complete
      attempts shouldBe 5
    }

    "retry with delay" in {
      val reportStatuses = Seq(Started, Started, InProgress, InProgress, Complete)
      var attempts = 0

      def testBody = {
        val body = Attempt.Right(reportStatuses(attempts))
        attempts = attempts + 1
        body
      }
      val begin = System.currentTimeMillis()
      Retry.until(testBody, predicateF, failMessage, testDelay).value() shouldBe Complete
      attempts shouldBe 5
      val end = System.currentTimeMillis()
      val diff = end - begin
      diff should (be >= 40L and be <= 1000L)
    }

    "retry with delay and fail" in {
      var attempts = 0
      def testBody = {
        val body = Attempt.Right(InProgress)
        attempts = attempts + 1
        body
      }
      val begin = System.currentTimeMillis()
      Retry.until(testBody, predicateF, failMessage, testDelay).isFailedAttempt() shouldBe true
      attempts shouldBe 10
      val end = System.currentTimeMillis()
      val diff = end - begin
      diff should (be >= 90L and be <= 1000L)
    }
  }
}
