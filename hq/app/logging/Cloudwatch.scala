package logging

import logic.CredentialsReportDisplay.{ReportSummary, reportStatusSummary}
import model.{AwsAccount, CredentialReportDisplay}
import play.api.Logging
import utils.attempt.FailedAttempt

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}


object Cloudwatch extends Logging {

  val cloudwatchClient = CloudWatchClient.builder.build()

  val defaultNamespace = "SecurityHQ"

  object DataType extends Enumeration {
    val s3Total = Value("s3/total")
    val iamCredentialsTotal = Value("iam/credentials/total")
    val iamCredentialsCritical = Value("iam/credentials/critical")
    val iamCredentialsWarning = Value("iam/credentials/warning")
    val iamKeysTotal = Value("iam/keys/total")
  }

  object ReaperExecutionStatus extends Enumeration {
    val success = Value("Success")
    val failure = Value("Failure")
  }

  def logMetricsForCredentialsReport(data: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] ) : Unit = {
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

  def logAsMetric[T](data: Map[AwsAccount, Either[FailedAttempt, List[T]]], dataType: DataType.Value ) : Unit = {
    data.toSeq.foreach {
      case (account: AwsAccount, Right(details: List[T])) =>
        putAwsMetric(account, dataType, details.length)
      case (account: AwsAccount, Left(_)) =>
        logger.error(s"Attempt to log cloudwatch metric failed. Data of type ${dataType} is missing for account ${account.name}.")
    }
  }

  def putAwsMetric(account: AwsAccount, dataType: DataType.Value , value: Int): Unit = {
    putMetric(defaultNamespace, "Vulnerabilities", Seq(("Account", account.name),("DataType", dataType.toString)), value)
  }

  def putIamRemovePasswordMetric(reaperExecutionStatus: ReaperExecutionStatus.Value, value: Int): Unit = {
    putMetric(defaultNamespace, "IamRemovePassword", Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)), value)
  }

  def putIamDisableAccessKeyMetric(reaperExecutionStatus: ReaperExecutionStatus.Value): Unit = {
    putMetric(defaultNamespace, "IamDisableAccessKey", Seq(("ReaperExecutionStatus", reaperExecutionStatus.toString)), 1)
  }

  private def putMetric(namespace: String, metricName: String, metricDimensions: Seq[(String, String)] , value: Int): Unit = {
    val dimension = metricDimensions.map( d => Dimension.builder.name(d._1).value(d._2).build()).toList
    val datum = MetricDatum.builder.metricName(metricName).unit(StandardUnit.COUNT).value(value.toDouble).dimensions(dimension.asJava).build()
    val request = PutMetricDataRequest.builder.namespace(namespace).metricData(datum).build()

    Try(cloudwatchClient.putMetricData(request)) match {
      case Success(_) => logger.debug(s"putMetric success: $datum")
      case Failure(e) => logger.error(s"putMetric failure: $datum", e)
    }
  }
}
