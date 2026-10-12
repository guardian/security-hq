package com.gu.shq.outdatedcredentials

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

class Lambda extends RequestStreamHandler {

  implicit val ec: ExecutionContext = ExecutionContext.global

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {

    val settings = Settings.fromEnvironment()

    val result = IamOutdatedCredentials.disableOutdatedCredentials(settings)

    Await.result(result.underlying, 10.minutes) match {
      case Right(_) => ()

      case Left(failedAttempt) =>
        throw new RuntimeException(
          s"IamOutdatedCredentials Lambda execution failed: ${failedAttempt.logMessage}",
          failedAttempt.firstException.orNull
        )
    }
  }

}
