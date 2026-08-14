package logging

import com.typesafe.scalalogging.LazyLogging
import logic.CredentialsReportDisplay.reportStatusSummary
import model.{AwsAccount, CredentialReportDisplay}
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.cloudwatch.model.*
import software.amazon.awssdk.regions.Region
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

object Cloudwatch extends LazyLogging {

  private val cloudwatchClient = CloudWatchAsyncClient.builder.region(Region.EU_WEST_1).build()

  private val defaultNamespace = "SecurityHQ"

  private object DataType extends Enumeration {
    val s3Total: Value = Value("s3/total")
    val iamCredentialsTotal: Value = Value("iam/credentials/total")
    val iamCredentialsCritical: Value = Value("iam/credentials/critical")
    val iamCredentialsWarning: Value = Value("iam/credentials/warning")
    val iamKeysTotal: Value = Value("iam/keys/total")
  }

  private object MetricName extends Enumeration {
    val iamDisableOutdatedKeys: Value = Value("IamDisableOutdatedKeys")
    val iamDisableAccessKey: Value = Value("IamDisableAccessKey")
    val iamRemovePassword: Value = Value("IamRemovePassword")
    val vulnerabilities: Value = Value("Vulnerabilities")
  }

  object ReaperExecutionStatus extends Enumeration {
    val success: Value = Value("Success")
    val failure: Value = Value("Failure")
  }

  def logMetricsForCredentialsReport(
      data: Map[AwsAccount, CredentialReportDisplay]
  )(using ExecutionContext): Attempt[Unit] = {
    val metricAttempts = data.flatMap { (account, data) =>
      val reportSummary = reportStatusSummary(data)
      List(
        putAwsMetric(account, DataType.iamCredentialsCritical, reportSummary.errors),
        putAwsMetric(account, DataType.iamCredentialsWarning, reportSummary.warnings),
        putAwsMetric(account, DataType.iamCredentialsTotal, reportSummary.errors + reportSummary.warnings)
      )
    }
    Attempt
      .traverse(metricAttempts.toList) { _ =>
        Attempt.Left(
          FailedAttempt(
            List(
              Failure(
                message = s"Failed to log cloudwatch metric for credentials report."
              )
            )
          )
        )
      }
      .map(_ => ())
  }

  def logExposedKeysMetric[T](
      data: Map[AwsAccount, List[T]]
  )(using ExecutionContext): Attempt[Unit] = {
    logAsMetric(data = data, dataType = DataType.iamKeysTotal)
  }

  def logS3TotalMetric[T](
      data: Map[AwsAccount, List[T]]
  )(using ExecutionContext): Attempt[Unit] = {
    logAsMetric(data = data, dataType = DataType.s3Total)
  }

  private def logAsMetric[T](data: Map[AwsAccount, List[T]], dataType: DataType.Value)(implicit
      executionContext: ExecutionContext
  ): Attempt[Unit] = {
    val metricsAttempts = data.map { case (account, details) =>
      putAwsMetric(account, dataType, details.length)
    }
    Attempt
      .traverse(metricsAttempts.toList) { _ =>
        Attempt.Left(
          FailedAttempt(
            List(
              Failure(
                message = s"Failed to log cloudwatch metric for data of type $dataType."
              )
            )
          )
        )
      }
      .map(_ => ())
  }

  private def putAwsMetric(account: AwsAccount, dataType: DataType.Value, value: Int)(implicit
      executionContext: ExecutionContext
  ): Attempt[Unit] = {
    putMetric(
      defaultNamespace,
      MetricName.vulnerabilities,
      Seq(("Account", account.name), ("DataType", dataType.toString)),
      value
    )
  }

  def putIamRemovePasswordMetric(reaperExecutionStatus: ReaperExecutionStatus.Value, value: Int)(implicit
      executionContext: ExecutionContext
  ): Attempt[Unit] = {
    putMetric(
      defaultNamespace,
      MetricName.iamRemovePassword,
      Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)),
      value
    )
  }

  def putIamDisableAccessKeyMetric(reaperExecutionStatus: ReaperExecutionStatus.Value, value: Int = 1)(implicit
      executionContext: ExecutionContext
  ): Attempt[Unit] = {
    putMetric(
      defaultNamespace,
      MetricName.iamDisableAccessKey,
      Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)),
      value
    )
  }

  def putIamDisableOutdatedKeysMetric(reaperExecutionStatus: ReaperExecutionStatus.Value, value: Int = 1)(implicit
      executionContext: ExecutionContext
  ): Attempt[Unit] = {
    putMetric(
      defaultNamespace,
      MetricName.iamDisableOutdatedKeys,
      Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)),
      value
    )
  }

  private def putMetric(
      namespace: String,
      metricName: MetricName.Value,
      metricDimensions: Seq[(String, String)],
      value: Int
  )(implicit ec: ExecutionContext): Attempt[Unit] = {
    val dimension = metricDimensions.map(d => Dimension.builder.name(d._1).value(d._2).build()).toList
    val datum = MetricDatum.builder
      .metricName(metricName.toString)
      .unit(StandardUnit.COUNT)
      .value(value.toDouble)
      .dimensions(dimension.asJava)
      .build()
    val request = PutMetricDataRequest.builder.namespace(namespace).metricData(datum).build()

    val future: Future[Unit] = cloudwatchClient.putMetricData(request).asScala.map(_ => ())

    Attempt.fromFuture(future) { case exception =>
      Failure(
        message = s"Failed to put metric data to CloudWatch: ${exception.getMessage}",
        throwable = Some(exception)
      ).attempt
    }
  }

  /** Emits a metric to CloudWatch based on the outcome of an Attempt.
    *
    * If the Attempt is successful, it will execute the onSuccess block and return the resulting attempt.
    *
    * If the Attempt fails, it will execute the onFailure block and return the resulting Attempt if it fails, or the
    * original failure if the onFailure block succeeds.
    *
    * @param result
    *   Any attempt
    * @param onSuccess
    *   The action to perform if the result is successful, typically emitting a success metric.
    * @param onFailure
    *   The action to perform if the result is a failure, typically emitting a failure metric.
    * @param actionLabel
    *   A label describing the action being performed, used for logging purposes.
    * @return
    *   An Attempt that represents the outcome of the original result and the metric emission.
    */
  def emitOutcomeMetric[T](
      result: Attempt[T],
      onSuccess: => Attempt[Unit],
      onFailure: => Attempt[Unit],
      actionLabel: String
  )(using ExecutionContext): Attempt[Unit] =
    Attempt
      .fromFuture(
        result.underlying.flatMap {
          case Left(failure) =>
            logger.error(s"$actionLabel failed: ${failure.logMessage}")
            onFailure.flatMap(_ => Attempt.Left(failure)).asFuture
          case Right(_) =>
            onSuccess.asFuture
        }
      )(e => {
        logger.error(s"Failed to emit $actionLabel metric: ${e.getMessage}", e)
        FailedAttempt(List(Failure(e.getMessage)))
      })
      .map(_ => ())
}
