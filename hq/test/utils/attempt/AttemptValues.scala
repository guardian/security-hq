package utils.attempt

import java.io.{ByteArrayOutputStream, PrintWriter}

import org.scalatest.exceptions.TestFailedException

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers


trait AttemptValues extends Matchers {
  implicit class RichAttempt[A](attempt: Attempt[A]) {
    private def stackTrace(failure: Failure) = {
      failure.throwable.map { t =>
        val baos = new ByteArrayOutputStream()
        val pw = new PrintWriter(baos)
        t.printStackTrace(pw)
        pw.close()
        baos.toString
      }.getOrElse("")
    }

    def value()(implicit ec: ExecutionContext): A = {
      val result = Await.result(attempt.asFuture, 5.seconds)
      withClue {
        result.fold(
          fa => s"${fa.failures.map(_.message).mkString(", ")} - ${fa.failures.map(stackTrace).mkString("\n\n")}",
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
    def getFailedAttempt()(implicit ec: ExecutionContext): FailedAttempt = {
      Await.result(attempt.asFuture, 5.seconds).fold (
        fa => fa,
        _ => throw new TestFailedException("Could not extract failure from successful Attempt", 10)
      )
    }
  }
}
