package com.gu.shq.unrecognisedusers

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import software.amazon.awssdk.regions.Region

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

/** AWS lambda entrypoint. The trigger payload is ignored. */
class Lambda extends RequestStreamHandler {

  // Leave a buffer below the Lambda's remaining time so a timeout is logged cleanly before AWS kills the invocation.
  private val timeoutBuffer = 10.seconds

  override def handleRequest(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit = {
    def required(key: String): String =
      sys.env.getOrElse(
        key,
        throw new RuntimeException(s"Missing required environment variable $key")
      )

    val timeout = (context.getRemainingTimeInMillis.millis - timeoutBuffer).max(0.millis)

    val settings = Settings(
      dryRun = sys.env.getOrElse(DRY_RUN, "true").toBoolean,
      configBucket = required(CONFIG_BUCKET),
      configKey = required(CONFIG_KEY),
      janusBucket = required(IAM_UNRECOGNISED_USER_S3_BUCKET),
      janusKey = required(IAM_UNRECOGNISED_USER_S3_KEY),
      region = Region.of(sys.env.getOrElse(REGION, "eu-west-1")),
      timeout = timeout
    )
    IamUnrecognisedUsers.run(settings)
  }

  private val DRY_RUN = "DRY_RUN"
  private val CONFIG_BUCKET = "CONFIG_BUCKET"
  private val CONFIG_KEY = "CONFIG_KEY"
  private val IAM_UNRECOGNISED_USER_S3_BUCKET = "IAM_UNRECOGNISED_USER_S3_BUCKET"
  private val IAM_UNRECOGNISED_USER_S3_KEY = "IAM_UNRECOGNISED_USER_S3_KEY"
  private val REGION = "REGION"
}
