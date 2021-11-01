package utils.attempt

import java.util.{Timer, TimerTask}

import play.api.Logging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try



/**
  * Represents a value that will need to be calculated using an asynchronous
  * computation that may fail.
  */
case class Attempt[A] private (underlying: Future[Either[FailedAttempt, A]]) extends Logging {
  def map[B](f: A => B)(implicit ec: ExecutionContext): Attempt[B] =
    flatMap(a => Attempt.Right(f(a)))

  def flatMap[B](f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[B] = Attempt {
    asFuture.flatMap {
      case Right(a) => f(a).asFuture
      case Left(e) => Future.successful(Left(e))
    }
  }

  def fold[B](failure: FailedAttempt => B, success: A => B)(implicit ec: ExecutionContext): Future[B] = {
    asFuture.map(_.fold(failure, success))
  }

  def map2[B, C](bAttempt: Attempt[B])(f: (A, B) => C)(implicit ec: ExecutionContext): Attempt[C] = {
    for {
      a <- this
      b <- bAttempt
    } yield f(a, b)
  }

  /**
    * If there is an error in the Future itself (e.g. a timeout) we convert it to a
    * Left so we have a consistent error representation. Unfortunately, this means
    * the error isn't being handled properly so we're left with just the information
    * provided by the exception.
    *
    * Try to avoid hitting this method's failure case by always handling Future errors
    * and creating a suitable failure instance for the problem.
    */
  def asFuture(implicit ec: ExecutionContext): Future[Either[FailedAttempt, A]] = {
    underlying recover { case err =>
      val apiErrors = FailedAttempt(Failure(err.getMessage, "Unexpected error", 500, throwable = Some(err)))
      logger.error(apiErrors.logMessage, apiErrors.firstException.orNull)
      scala.Left(apiErrors)
    }
  }

  def delay(delay: FiniteDuration)(implicit ec: ExecutionContext): Attempt[A] = {
    flatMap(Attempt.Delayed(delay)(_))
  }
}

object Attempt {
  /**
    * Changes generated `List[Attempt[A]]` to `Attempt[List[A]]` via provided
    * traversal function (like `Future.traverse`).
    *
    * This implementation returns the first failure in the resulting list,
    * or the successful result.
    */
  def traverse[A, B](as: List[A])(f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[List[B]] = {
    as.foldRight[Attempt[List[B]]](Right(Nil))(f(_).map2(_)(_ :: _))
  }

  /** Using the provided traversal function, sequence the resulting attempts
    * to `Attempt[List[(A, B)]]` where the first element of each tuple is the
    * value passed to the function f in order to to generate the second element
    *
    * This implementation returns the first failure in the resulting list,
    * or the successful result.
    */
  def labelledTraverse[A, B](as: List[A])(f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[List[(A, B)]] = {
    Attempt.traverse(as)(a => f(a).map((a, _)))
  }

  /** Traverses a list of tuples, where an effectful function is run on the second element of the tuple
    * and the first element is returned unchanged.
    */
  def tupleTraverse[A, B, C, D](abs: List[(A, B, C)])(f: C => Attempt[D])(implicit ec: ExecutionContext): Attempt[List[(A, B, D)]] = {
    Attempt.traverse(abs){ case (a, b, c) =>
      f(c).map(c => (a, b, c))
    }
  }

  //TODO
  def tupleTraverseAgain[A, B, C](abs: List[(A, List[B])])(f: B => Attempt[C])(implicit ec: ExecutionContext): Attempt[List[(A, List[C])]] = {
    Attempt.traverse(abs){ case (a, bs) =>
      ???
    }
  }

  /** Traverses the given list `List[A]` with the function f, A => Attempt[List[B]]` and flattens the generated result into Attempt[List[B]]`
    * This implementation returns the first failure in the resulting list,
    * or the successful result.
    */
  def flatTraverse[A, B](as: List[A])(f: A => Attempt[List[B]])(implicit ec: ExecutionContext): Attempt[List[B]] = {
    Attempt.traverse(as)(f).map(_.flatten)
  }

  /**
    * Using the provided traversal function, sequence the resulting attempts
    * into a list that preserves failures.
    *
    * This is useful if failure is acceptable in part of the application.
    */
  def traverseWithFailures[A, B](as: List[A])(f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[List[Either[FailedAttempt, B]]] = {
    sequenceWithFailures(as.map(f))
  }

  /**
    * Using the provided traversal function, sequence the resulting attempts
    * into a list that:
    * - preserves failures (by keeping the resulting Either)
    * - creates a tuple who's first element is the value passed to the traversal function f
    *
    * Combines the behaviours of labelledTraverse and traverseWithFailures.
    */
  def labelledTraverseWithFailures[A, B](as: List[A])(f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[List[(A, Either[FailedAttempt, B])]] = {
    Async.Right(Future.traverse(as)(a => f(a).asFuture.map(a -> _)))
  }

  /**
    * Flattens the given list `List[Attempt[List[A]]]` to `Attempt[List[A]]`.
    */
  def flatSequence[A](as: List[Attempt[List[A]]])(implicit ec: ExecutionContext): Attempt[List[A]] = {
    flatTraverse(as)(identity)
  }

  /**
    * As with `Future.sequence`, changes `List[Attempt[A]]` to `Attempt[List[A]]`.
    *
    * This implementation returns the first failure in the list, or the successful result.
    */
  def sequence[A](responses: List[Attempt[A]])(implicit ec: ExecutionContext): Attempt[List[A]] = {
    traverse(responses)(identity)
  }

  /**
    * Sequence these attempts into a list that preserves failures.
    *
    * This is useful if failure is acceptable in part of the application.
    */
  def sequenceWithFailures[A](attempts: List[Attempt[A]])(implicit ec: ExecutionContext): Attempt[List[Either[FailedAttempt, A]]] = {
    Async.Right(Future.traverse(attempts)(_.asFuture))
  }

  def fromEither[A](e: Either[FailedAttempt, A]): Attempt[A] =
    Attempt(Future.successful(e))

  def fromOption[A](optA: Option[A], ifNone: FailedAttempt): Attempt[A] =
    fromEither(optA.toRight(ifNone))

  /**
    * Convert a plain `Future` value to an attempt by providing a recovery handler.
    */
  def fromFuture[A](future: Future[A])(recovery: PartialFunction[Throwable, FailedAttempt])(implicit ec: ExecutionContext): Attempt[A] = {
    Attempt {
      future
        .map(scala.Right(_))
        .recover { case t =>
          scala.Left(recovery(t))
        }
    }
  }

  /**
    * Discard failures from a list of attempts.
    *
    * **Use with caution**.
    */
  def successfulAttempts[A](attempts: List[Attempt[A]])(implicit ec: ExecutionContext): Attempt[List[A]] = {
    Attempt.Async.Right {
      Future.traverse(attempts)(_.asFuture).map(_.collect { case Right(a) => a })
    }
  }

  /**
    * Create an Attempt instance from a "good" value.
    */
  def Right[A](a: A): Attempt[A] =
    Attempt(Future.successful(scala.Right(a)))

  /**
    * Create an Attempt failure from an Failure instance, representing the possibility of multiple failures.
    */
  def Left[A](errs: FailedAttempt): Attempt[A] =
    Attempt(Future.successful(scala.Left(errs)))
  /**
    * Syntax sugar to create an Attempt failure if there's only a single error.
    */
  def Left[A](err: Failure): Attempt[A] =
    Attempt(Future.successful(scala.Left(FailedAttempt(err))))

  /**
    * Asyncronous versions of the Attempt Right/Left helpers for when you have
    * a Future that returns a good/bad value directly.
    */
  object Async {
    /**
      * Create an Attempt from a Future of a good value.
      */
    def Right[A](fa: Future[A])(implicit ec: ExecutionContext): Attempt[A] =
      Attempt(fa.map(scala.Right(_)))

    /**
      * Create an Attempt from a known failure in the future. For example,
      * if a piece of logic fails but you need to make a Database/API call to
      * get the failure information.
      */
    def Left[A](ferr: Future[FailedAttempt])(implicit ec: ExecutionContext): Attempt[A] =
      Attempt(ferr.map(scala.Left(_)))
  }

  /**
    * @see https://stackoverflow.com/questions/16359849/scala-scheduledfuture
    **/
  object Delayed {
    private val timer = new Timer(true)

    private def makeTask[A](body: => A)(schedule: TimerTask => Unit)(implicit ctx: ExecutionContext): Future[A] = {
      val prom = Promise[A]()
      schedule(
        new TimerTask {
          def run() {
            ctx.execute(
              new Runnable {
                def run() {
                  prom.complete(Try(body))
                }
              }
            )
          }
        }
      )
      prom.future
    }

    def apply[A](delay: FiniteDuration)(body: => A)(implicit ctx: ExecutionContext): Attempt[A] = {
      Attempt.fromFuture(makeTask(body)(timer.schedule(_, delay.toMillis))) {
        case th => Failure(th.getMessage, "Cannot execute the delayed task", 500).attempt
      }
    }
  }

}
