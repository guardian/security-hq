package logic

import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
object Retry {

  /**
    * execute the body until the predicate is satisfied or maxAttempt is reached, with the delay between each body execution.
    * @param body
    * @param predicate
    * @param failureMessage
    * @param delay
    * @param maxAttempt
    * @param ec
    * @tparam A
    * @return
    */
  def until[A](body: => Attempt[A], predicate: A => Boolean, failureMessage: String, delay: FiniteDuration , maxAttempt: Int = 10)(implicit ec: ExecutionContext): Attempt[A] = {
    def loop(numberOfTries: Int): Attempt[A] = {
      if (numberOfTries >= maxAttempt ) {
        Attempt.Left[A](Failure(s"MAX_ATTEMPT_LIMIT_REACHED: $failureMessage", failureMessage, 500))
      } else {
        val noOpDelay = Attempt.Async.Right(DelayedFuture(delay)(Unit))
        for {
          result <- body
          next  <- if (predicate(result)) Attempt.Right(result) else {
            noOpDelay flatMap ( _ => loop(numberOfTries + 1))
          }
        } yield next
      }
    }
    loop(0)
  }


}


/**
  * @see https://stackoverflow.com/questions/16359849/scala-scheduledfuture
  * */
object DelayedFuture {
  import java.util.{Timer, TimerTask}

  import scala.concurrent._
  import scala.concurrent.duration.FiniteDuration
  import scala.util.Try
  private val timer = new Timer(true)

  private def makeTask[T]( body: => T )( schedule: TimerTask => Unit )(implicit ctx: ExecutionContext): Future[T] = {
    val prom = Promise[T]()
    schedule(
      new TimerTask{
        def run() {
          // IMPORTANT: The timer task just starts the execution on the passed
          // ExecutionContext and is thus almost instantaneous (making it
          // practical to use a single  Timer - hence a single background thread).
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
  def apply[T]( delay: FiniteDuration )( body: => T )(implicit ctx: ExecutionContext): Future[T] = {
    makeTask( body )( timer.schedule( _, delay.toMillis ) )
  }
}
