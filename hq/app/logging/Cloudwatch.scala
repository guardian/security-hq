package logging

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, PutMetricDataResult, StandardUnit}
import model.{AwsAccount, CredentialReportDisplay}
import play.api.Logging

import collection.JavaConverters._
import scala.util.{Failure, Success, Try}


object Cloudwatch extends Logging {

  object DataType extends Enumeration {
    val s3Total = Value("s3/total")
    val iamCredentialsTotal = Value("iam/credentials/total")
    val iamKeysTotal = Value("iam/keys/total")
    val sgTotal = Value("securitygroup/total")
  }

  def logAsMetric[T](data: Seq[T], dataType: DataType.Value ) : Unit = {
    for ((account: AwsAccount, result) <- data) {
      result match {
        case Right(details: CredentialReportDisplay) => {
          putMetric(account, dataType, details.humanUsers.length + details.machineUsers.length)
        }
        case Right(details: List[Any]) => {
          putMetric(account, dataType, details.length)
        }
        case Left(_) => {
          logger.error(s"Attempt to log cloudwatch metric failed. Data of type ${dataType} is missing for account ${account.name}.")
        }
      }
    }
  }

  def putMetric(account: AwsAccount, dataType: DataType.Value , value: Int): Unit = {
    val cw = AmazonCloudWatchClientBuilder.defaultClient

    val dimension = List(
      (new Dimension).withName("Account").withValue(account.name),
      (new Dimension).withName("DataType").withValue(dataType.toString)
    )
    val datum = new MetricDatum().withMetricName("Vulnerabilities").withUnit(StandardUnit.Count).withValue(value.toDouble).withDimensions(dimension.asJava)
    val request = new PutMetricDataRequest().withNamespace("SecurityHQ").withMetricData(datum)
    Try(cw.putMetricData(request)) match {
      case Success(response) => logger.info(s"METRIC:  Account=${account.name},DataType=${dataType},Value=${value}")
      case Failure(e: AmazonServiceException) => logger.error(s"Put metric of type ${dataType} failed for account ${account.name}", e)
    }
  }
}
