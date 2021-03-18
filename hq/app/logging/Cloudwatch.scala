package logging

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import logic.CredentialsReportDisplay
import logic.CredentialsReportDisplay.reportStatusSummary
import model.{AwsAccount, CredentialReportDisplay}
import play.api.Logging
import utils.attempt.FailedAttempt

import collection.JavaConverters._
import scala.util.{Failure, Success, Try}


object Cloudwatch extends Logging {

  val cloudwatchClient = AmazonCloudWatchClientBuilder.defaultClient

  object DataType extends Enumeration {
    val s3Total = Value("s3/total")
    val iamCredentialsTotal = Value("iam/credentials/total")
    val iamCredentialsCritical = Value("iam/credentials/critical")
    val iamCredentialsWarning = Value("iam/credentials/warning")
    val iamKeysTotal = Value("iam/keys/total")
    val sgTotal = Value("securitygroup/total")
  }

  def logMetricsForCredentialsReport(data: Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])] ) : Unit = {
    data.foreach {
      case (account: AwsAccount, Right(details: CredentialReportDisplay)) =>
        val reportSummary: CredentialsReportDisplay.ReportSummary = reportStatusSummary(details)
        putMetric(account, DataType.iamCredentialsCritical, reportSummary.errors)
        putMetric(account, DataType.iamCredentialsWarning, reportSummary.warnings)
        putMetric(account, DataType.iamCredentialsTotal, reportSummary.errors + reportSummary.warnings)
      case (account: AwsAccount, Left(_)) =>
        logger.error(s"Attempt to log cloudwatch metric failed. IAM data is missing for account ${account.name}.")
    }
  }

  def logAsMetric[T](data: Seq[T], dataType: DataType.Value ) : Unit = {
    data.foreach {
      case (account: AwsAccount, Right(details: List[Any])) =>
        putMetric(account, dataType, details.length)
      case (account: AwsAccount, Left(_)) =>
        logger.error(s"Attempt to log cloudwatch metric failed. Data of type ${dataType} is missing for account ${account.name}.")
    }
  }

  def putMetric(account: AwsAccount, dataType: DataType.Value , value: Int): Unit = {
    val dimension = List(
      (new Dimension).withName("Account").withValue(account.name),
      (new Dimension).withName("DataType").withValue(dataType.toString)
    )

    val datum = new MetricDatum().withMetricName("Vulnerabilities").withUnit(StandardUnit.Count).withValue(value.toDouble).withDimensions(dimension.asJava)
    val request = new PutMetricDataRequest().withNamespace("SecurityHQ").withMetricData(datum)

    Try(cloudwatchClient.putMetricData(request)) match {
      case Success(response) => logger.info(s"METRIC:  Account=${account.name},DataType=${dataType},Value=${value}")
      case Failure(e: AmazonServiceException) => logger.error(s"Put metric of type ${dataType} failed for account ${account.name}", e)
      case Failure(e) => logger.error(s"Put metric of type ${dataType} failed for account ${account.name} with an unknown exception", e)
    }
  }
}
