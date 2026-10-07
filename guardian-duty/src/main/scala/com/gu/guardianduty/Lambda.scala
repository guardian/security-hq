package com.gu.guardianduty

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsAsyncClient

import java.io.{InputStream, OutputStream}
import java.time.{Duration, LocalDate}
import scala.concurrent.ExecutionContext

/** AWS Lambda entry point. Receives EventBridge events containing GuardDuty findings.
  */
class Lambda extends RequestStreamHandler {
  private given ExecutionContext = ExecutionContext.global

  private val topicArn: String =
    sys.env.getOrElse(
      "ANGHAMMARAD_SNS_ARN",
      throw new Exception("Missing Anghammarad SNS ARN, notifications cannot be sent")
    )
  private val runbookUrl: String =
    sys.env.getOrElse("RUNBOOK_URL", throw new Exception("Missing runbook url configuration"))

  private val crtAsyncHttpClient = AwsCrtAsyncHttpClient
    .builder()
    .connectionTimeout(Duration.ofSeconds(3))
    .maxConcurrency(100)
    .build()
  private val snsClient = SnsAsyncClient
    .builder()
    // Anghammarad's SNS topic is in eu-west-1
    .region(Region.EU_WEST_1)
    // this client results in much faster cold start times than the default
    .httpClient(crtAsyncHttpClient)
    // this skips credential provider resolution to improve slow start time
    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
    .build()
  private val anghammaradNotifications =
    AnghammaradNotifications(topicArn, Some(snsClient))

  override def handleRequest(
      input: InputStream,
      output: OutputStream,
      context: Context
  ): Unit = {
    val services = Services(
      notifications = anghammaradNotifications,
      logger = LambdaLogger(context)
    )
    val rawJson = new String(input.readAllBytes(), "UTF-8")
    val today = LocalDate.now()
    GuardianDuty.processEvent(rawJson, services, today, runbookUrl)
  }
}
