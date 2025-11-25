package utils

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream

import scala.concurrent.Future
import scala.concurrent.duration.*

object Scheduler {

  /** Schedule a job to run at a fixed rate using FS2 streams.
    *
    * Using an FS2 stream implementation instead of Play's provided pekko scheduler because the pekko scheduler suffers
    * from unexpected behaviour when it misses intervals. It will immediately run all missed jobs on the next schedule
    * checkpoint. This can DoS any service the scheduled jobs are hitting.
    *
    * @param initialDelay
    *   The delay before the first execution
    * @param interval
    *   The interval between subsequent executions
    * @param job
    *   The job to execute (will be run on a blocking context)
    * @return
    *   A function that when called cancels the scheduled task and returns a Future[Unit]
    */
  def scheduleAtFixedRate(initialDelay: FiniteDuration, interval: FiniteDuration)(job: () => Unit): () => Future[Unit] =
    (Stream.sleep[IO](initialDelay) ++ Stream.awakeEvery[IO](interval))
      .evalMap { _ =>
        // Wrap job execution to handle exceptions and prevent stream termination
        IO.blocking(job()).handleErrorWith { error =>
          // Log error but don't fail the stream
          IO.println(s"Scheduled job failed with error: ${error.getMessage}")
        }
      }
      .compile
      .drain
      .unsafeRunCancelable()
}
