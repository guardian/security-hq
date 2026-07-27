package logic

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import settings.Settings
import utils.attempt.FailedAttempt

import java.io.{InputStream, OutputStream}
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

class IamOutdatedCredentialsLambda extends RequestStreamHandler {

  implicit val ec: ExecutionContext = ExecutionContext.global

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {

    val settings = Settings.fromEnvironment()

    val result = IamOutdatedCredentials.disableOutdatedCredentials(settings)

    Await.result(result.underlying, 10.minutes) match {
      case Right(_) => ()

      case Left(failedAttempt) =>
        val (expectedFailures, otherFailures) = failedAttempt.failures.partition(_.isExpected)
        if (otherFailures.nonEmpty) {
          val attempt = FailedAttempt(otherFailures)
          throw new RuntimeException(
            s"IamOutdatedCredentials Lambda execution failed: ${attempt.logMessage}",
            attempt.firstException.orNull
          )
        } else {
          ()
        }
    }
  }

}
