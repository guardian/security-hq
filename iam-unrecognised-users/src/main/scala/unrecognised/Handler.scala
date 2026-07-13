package unrecognised

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}
import scala.concurrent.duration.*

/** AWS lambda entrypoint. The trigger payload is ignored. */
class Handler extends RequestStreamHandler {

  // Leave a buffer below the Lambda's remaining time so a timeout is logged cleanly before AWS kills the invocation.
  private val timeoutBuffer = 10.seconds

  override def handleRequest(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit = {
    val timeout = (context.getRemainingTimeInMillis.millis - timeoutBuffer).max(0.millis)
    UnrecognisedUsers.run(timeout = timeout)
  }
}
