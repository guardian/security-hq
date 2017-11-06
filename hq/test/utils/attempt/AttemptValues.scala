package utils.attempt

import org.scalatest.exceptions.TestFailedException

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._


trait AttemptValues {
  implicit class RichAttempt[A](attempt: Attempt[A]) {
    def value()(implicit ec: ExecutionContext): A = {
      Await.result(attempt.asFuture, 5.seconds).fold[A] (
        { fa =>
          throw new TestFailedException(fa.logString, 10)
        },
        identity
      )
    }

    def isFailedAttempt()(implicit ec: ExecutionContext): Boolean = {
      Await.result(attempt.asFuture, 5.seconds).fold (
        _ => true,
        _ => false
      )
    }
  }
}
