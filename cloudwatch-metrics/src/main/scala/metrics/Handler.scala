package metrics

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}

/** AWS Lambda entrypoint. Invoked on the 6-hourly schedule defined in the CDK
  * stack. The trigger payload is ignored.
  */
class Handler extends RequestStreamHandler {
  override def handleRequest(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit =
    MetricsCollector.run()
}
