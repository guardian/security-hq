package logic

import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
object Retry {

  /**
    * execute the body until the predicate is satisfied or maxAttempt is reached, with the delay between each body execution.
    */
  def until[A](body: => Attempt[A], predicate: A => Boolean, failureMessage: String, delay: FiniteDuration , maxAttempt: Int = 10)(implicit ec: ExecutionContext): Attempt[A] = {
    def determineBody(tryCnt: Int) : Attempt[A] =  if (tryCnt == 0) body else body.delay(delay)

    def loop(numberOfTries: Int): Attempt[A] = {
      if (numberOfTries >= maxAttempt ) {
        Attempt.Left[A](Failure(s"MAX_ATTEMPT_LIMIT_REACHED: $failureMessage", failureMessage, 500))
      } else {
        for {
          result <- determineBody(numberOfTries)
          next  <- if (predicate(result)) Attempt.Right(result) else loop(numberOfTries + 1)
        } yield next
      }
    }

    loop(0)
  }


}

