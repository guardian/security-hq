package utils.attempt

import org.scalatest.Matchers
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._


trait AttemptValues extends Matchers {
  implicit class RichAttempt[A](attempt: Attempt[A]) {
    def value()(implicit ec: ExecutionContext): A = {
      val result = Await.result(attempt.asFuture, 5.seconds)
      withClue {
        result.fold(
          fa => s"${fa.logString} -",
          _ => ""
        )
      } {
        result.fold[A](
          _ => throw new TestFailedException("Could not extract value from failed Attempt", 10),
          identity
        )
      }
    }

    def isFailedAttempt()(implicit ec: ExecutionContext): Boolean = {
      Await.result(attempt.asFuture, 5.seconds).fold (
        _ => true,
        _ => false
      )
    }
  }
}
