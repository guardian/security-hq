package utils.attempt

import org.scalatest.exceptions.TestFailedException

import scala.concurrent.Await
import scala.concurrent.duration._


trait AttemptValues {
  implicit class RichAttempt[A](attempt: Attempt[A]) {
    def value(): A = {
      Await.result(attempt.asFuture, 5.seconds).fold (
        { fa =>
          throw new TestFailedException(fa.logString, 10)
        },
        identity
      )
    }

    def isFailedAttempt(): Boolean = {
      Await.result(attempt.asFuture, 5.seconds).fold (
        _ => true,
        _ => false
      )
    }
  }
}
