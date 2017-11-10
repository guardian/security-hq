package logic

import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
object Retry {

  def until[A](body: => Attempt[A], predicate: A => Boolean, failureMessage: String, maxAttempt: Int = 10)(implicit ec: ExecutionContext): Attempt[A] = {

    def loop(numberOfTries: Int): Attempt[A] = {
      if (numberOfTries >= maxAttempt ) {
        Attempt.Left[A](Failure("MAX_ATTEMPT_LIMIT", failureMessage, 500))
      } else {
        for {
          result <- body
          next  <- if (predicate(result)) Attempt.Right(result) else loop(numberOfTries + 1)
        } yield next
      }
    }
    loop(0)
  }

}

