package logging

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, PutMetricDataResult, StandardUnit}
import model.{AwsAccount, CredentialReportDisplay}

import collection.JavaConverters._


object Cloudwatch {

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
          println(s"Attempt to log cloudwatch metric failed. Data of type ${dataType} is missing for account ${account.name}.")
        }
      }
    }
  }

  def putMetric(account: AwsAccount, dataType: DataType.Value , value: Int): String = {
    println(s"METRIC:  Account=${account.name},DataType=${dataType},Value=${value}")
    val cw = AmazonCloudWatchClientBuilder.defaultClient

    val dimension = List(
      (new Dimension).withName("Account").withValue(account.name),
      (new Dimension).withName("DataType").withValue(dataType.toString)
    )
    val datum = new MetricDatum().withMetricName("Vulnerabilities").withUnit(StandardUnit.Count).withValue(value.toDouble).withDimensions(dimension.asJava)
    val request = new PutMetricDataRequest().withNamespace("SecurityHQ").withMetricData(datum)
    val response: PutMetricDataResult = cw.putMetricData(request)
    response.toString
  }
}
