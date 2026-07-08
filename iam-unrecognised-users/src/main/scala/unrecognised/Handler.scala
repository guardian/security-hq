package unrecognised

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}

/** AWS lambda entrypoint. The trigger payload is ignored. */
class Handler extends RequestStreamHandler {
  override def handleRequest(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit =
    UnrecognisedUsers.run()
}
