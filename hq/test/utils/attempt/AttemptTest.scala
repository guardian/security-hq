package utils.attempt

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import Attempt.{Left, Right}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import com.amazonaws.AbortedException


class AttemptTest extends FreeSpec with Matchers with EitherValues {
  import scala.concurrent.ExecutionContext.Implicits.global

  "fromFutureWithAcceptableFailures" - {

    val recoverToRight: PartialFunction[Throwable, String] = {
      case t if t.getMessage.contains("acceptable") => "accepted"
    }
    val failedAttempt = Failure.awsAccountNotFound("").attempt
    val recoverToLeft: PartialFunction[Throwable, FailedAttempt] = {
      case _: IllegalArgumentException => failedAttempt
    }

    "returns successful attempt if the future contains an acceptable error" in {
      val throwable = new Throwable("An acceptable exception")
      val future = Future.failed(throwable)

      val attempt = Attempt.fromFutureWithAcceptableFailure(future)(recoverToLeft, recoverToRight)
      attempt.awaitEither.right.value shouldEqual "accepted"
    }

    "returns the correct failed attempt if the future does not contain an acceptable error, but one we recover from" in {
      val throwable = new IllegalArgumentException("Recovered exception")
      val future = Future.failed(throwable)

      val attempt = Attempt.fromFutureWithAcceptableFailure(future)(recoverToLeft, recoverToRight)
      attempt.awaitEither.left.value shouldEqual failedAttempt
    }

    "returns the correct failed attempt if the future does not contain an acceptable error, or one we recover from" in {
      val throwable = new NullPointerException("Unrecovered exception")
      val future = Future.failed(throwable)

      val attempt = Attempt.fromFutureWithAcceptableFailure(future)(recoverToLeft, recoverToRight)
      attempt.awaitEither.left.value.firstException.get shouldBe throwable
    }
  }

  "traverse" - {
    "returns the first failure" in {
      def failOnFourAndSix(i: Int): Attempt[Int] = {
        i match {
          case 4 => expectedFailure("fails on four")
          case 6 => expectedFailure("fails on six")
          case n => Right(n)
        }
      }
      val errors = Attempt.traverse(List(1, 2, 3, 4, 5, 6))(failOnFourAndSix).awaitEither.left.value
      checkError(errors, "fails on four")
    }

    "returns the successful result if there were no failures" in {
      Attempt.traverse(List(1, 2, 3, 4))(Right).awaitEither.right.value shouldEqual List(1, 2, 3, 4)
    }
  }

  "flatTraverse" - {
    "returns the first failure" in {
      def failOnFourAndSix(i: Int): Attempt[List[Int]] = {
        i match {
          case 4 => expectedFailure("fails on four")
          case 6 => expectedFailure("fails on six")
          case n => Right(List(n))
        }
      }
      val errors = Attempt.flatTraverse(List(1, 2, 3, 4, 5, 6))(failOnFourAndSix).awaitEither.left.value
      checkError(errors, "fails on four")
    }

    "returns the successful result if there were no failures" in {
      Attempt.flatTraverse(List(1, 2, 3, 4))(a => Right(List(a))).awaitEither.right.value shouldEqual List(1, 2, 3, 4)
    }
  }

  "labelledTraverse" - {
    "returns the first failure" in {
      def failOnFourAndSix(i: Int): Attempt[List[Int]] = {
        i match {
          case 4 => expectedFailure("fails on four")
          case 6 => expectedFailure("fails on six")
          case n => Right(List(n))
        }
      }
      val errors = Attempt.labelledTraverse(List(1, 2, 3, 4, 5, 6))(failOnFourAndSix).awaitEither.left.value
      checkError(errors, "fails on four")
    }

    "returns the successful result if there were no failures" in {
      val result = Attempt.labelledTraverse(List(1, 2, 3, 4))(a => Right(List(a))).awaitEither.right.value
      result shouldEqual List((1, List(1)), (2, List(2)), (3, List(3)), (4, List(4)))
    }
  }

  "successfulAttempts" - {
    "returns the list if all were successful" in {
      val attempts = List(Right(1), Right(2))

      Attempt.successfulAttempts(attempts).awaitEither.right.value shouldEqual List(1, 2)
    }

    "returns only the successful attempts if there were failures" in {
      val attempts: List[Attempt[Int]] = List(Right(1), Right(2), expectedFailure("failed"), Right(4))

      Attempt.successfulAttempts(attempts).awaitEither.right.value shouldEqual List(1, 2, 4)
    }
  }

  /**
    * Utilities for checking the failure state of attempts
    */
  def checkError(errors: FailedAttempt, expected: String): Unit = {
    errors.failures.head.message shouldEqual expected
  }
  def expectedFailure[A](message: String): Attempt[A] = Left[A](Failure(message, "this will fail", 500))

  /**
    * Utility for dealing with futures in tests
    */
  implicit class RichAttempt[A](attempt: Attempt[A]) {
    def awaitEither = Await.result(attempt.asFuture, 5.seconds)
  }
}
