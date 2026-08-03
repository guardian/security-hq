package logging

import com.typesafe.scalalogging.LazyLogging
import logic.CredentialsReportDisplay.{ReportSummary, reportStatusSummary}
import model.{AwsAccount, CredentialReportDisplay}
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import utils.attempt.FailedAttempt

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object Cloudwatch extends LazyLogging {

  private val cloudwatchClient = CloudWatchClient.builder.build()

  private val defaultNamespace = "SecurityHQ"

  object DataType extends Enumeration {
    val s3Total: Value = Value("s3/total")
    val iamCredentialsTotal: Value = Value("iam/credentials/total")
    val iamCredentialsCritical: Value = Value("iam/credentials/critical")
    val iamCredentialsWarning: Value = Value("iam/credentials/warning")
    val iamKeysTotal: Value = Value("iam/keys/total")
  }

  private object MetricName extends Enumeration {
    val iamDisableOutdatedKeys = "IamDisableOutdatedKeys"
    val iamDisableAccessKey = "IamDisableAccessKey"
    val iamRemovePassword = "IamRemovePassword"
    val vulnerabilities = "Vulnerabilities"
  }

  object ReaperExecutionStatus extends Enumeration {
    val success: Value = Value("Success")
    val failure: Value = Value("Failure")
  }

  def logMetricsForCredentialsReport(data: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]): Unit = {
    data.toSeq.foreach {
      case (account: AwsAccount, Right(details: CredentialReportDisplay)) =>
        val reportSummary: ReportSummary = reportStatusSummary(details)
        putAwsMetric(account, DataType.iamCredentialsCritical, reportSummary.errors)
        putAwsMetric(account, DataType.iamCredentialsWarning, reportSummary.warnings)
        putAwsMetric(account, DataType.iamCredentialsTotal, reportSummary.errors + reportSummary.warnings)
      case (account: AwsAccount, Left(_)) =>
        logger.error(s"Attempt to log cloudwatch metric failed. IAM data is missing for account ${account.name}.")
    }
  }

  def logAsMetric[T](data: Map[AwsAccount, Either[FailedAttempt, List[T]]], dataType: DataType.Value): Unit = {
    data.toSeq.foreach {
      case (account: AwsAccount, Right(details: List[T])) =>
        putAwsMetric(account, dataType, details.length)
      case (account: AwsAccount, Left(_)) =>
        logger.error(
          s"Attempt to log cloudwatch metric failed. Data of type $dataType is missing for account ${account.name}."
        )
    }
  }

  private def putAwsMetric(account: AwsAccount, dataType: DataType.Value, value: Int): Unit = {
    putMetric(
      defaultNamespace,
      MetricName.vulnerabilities,
      Seq(("Account", account.name), ("DataType", dataType.toString)),
      value
    )
  }

  def putIamRemovePasswordMetric(reaperExecutionStatus: ReaperExecutionStatus.Value, value: Int): Unit = {
    putMetric(
      defaultNamespace,
      MetricName.iamRemovePassword,
      Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)),
      value
    )
  }

  def putIamDisableAccessKeyMetric(reaperExecutionStatus: ReaperExecutionStatus.Value): Unit = {
    putMetric(
      defaultNamespace,
      MetricName.iamDisableAccessKey,
      Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)),
      1
    )
  }

  def putIamDisableOutdatedKeysMetric(reaperExecutionStatus: ReaperExecutionStatus.Value): Unit = {
    putMetric(
      defaultNamespace,
      MetricName.iamDisableOutdatedKeys,
      Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)),
      1
    )
  }

  private def putMetric(
      namespace: String,
      metricName: String,
      metricDimensions: Seq[(String, String)],
      value: Int
  ): Unit = {
    val dimension = metricDimensions.map(d => Dimension.builder.name(d._1).value(d._2).build()).toList
    val datum = MetricDatum.builder
      .metricName(metricName)
      .unit(StandardUnit.COUNT)
      .value(value.toDouble)
      .dimensions(dimension.asJava)
      .build()
    val request = PutMetricDataRequest.builder.namespace(namespace).metricData(datum).build()

    Try(cloudwatchClient.putMetricData(request)) match {
      case Success(_) => logger.debug(s"putMetric success: $datum")
      case Failure(e) => logger.error(s"putMetric failure: $datum", e)
    }
  }
}
