package schedule

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest}
import model.{AwsAccount, IamAuditAlert, IamAuditUser}
import org.joda.time.DateTime
import play.api.Logging

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

trait AttributeValues {
  def S(str: String) = new AttributeValue().withS(str)
  def L(list: List[AttributeValue]) = new AttributeValue().withL(list.asJava)
  def N(number: Long) = new AttributeValue().withN(number.toString)
  def N(number: Double) = new AttributeValue().withN(number.toString)
  def B(boolean: Boolean) = new AttributeValue().withBOOL(boolean)
  def M(map: Map[String,  AttributeValue]) = new AttributeValue().withM(map.asJava)
}

class Dynamo(client: AmazonDynamoDB, tableName: Option[String]) extends AttributeValues with Logging {

  val table = tableName match {
    case Some(tableName) => tableName
    case None =>
      logger.error("unable to retrieve Iam Dynamo Table Name from config - check that table name is present in security-hq.conf in S3")
      "error"
  }

  def get(key: Map[String, AttributeValue]): Option[Map[String, AttributeValue]] = {
    try {
      Option(client.getItem(
        new GetItemRequest().withTableName(table).withKey(key.asJava)).getItem).map(_.asScala.toMap)
    } catch {
      case NonFatal(e) =>
        logger.error(s"unable to get item from dynamoDB table: ${e.getMessage}", e)
        None
    }
  }

  def getAlert(awsAccount: AwsAccount, username: String): Option[IamAuditUser] = {
    val key = Map("id" -> S(Dynamo.createId(awsAccount, username)))
    get(key).map { r =>

      val alerts = r("alerts").getL.asScala.map{ a =>
        val alertMap = a.getM.asScala
        IamAuditAlert(
          new DateTime(alertMap("date").getS.toLong),
          new DateTime(alertMap("disableDeadline").getS.toLong)
        )
      }.toList

      IamAuditUser(
        r("id").getS,
        r("awsAccount").getS,
        r("username").getS,
        alerts
      )
    }
  }

  def put(item: Map[String, AttributeValue]): Unit = try {
    client.putItem(
      new PutItemRequest().withTableName(table).withItem(item.asJava))
  } catch {
    case NonFatal(e) =>
    logger.error(s"unable to put item to dynamoDB table: ${e.getMessage}", e)
  }

  def alertToMap(e: IamAuditAlert): Map[String, AttributeValue] = {
    Map(
      "date" -> N(e.dateNotificationSent.getMillis),
      "disableDeadline" -> N(e.disableDeadline.getMillis)
    )
  }

  def putAlert(alert: IamAuditUser): Unit = put(Map(
    "id" -> S(alert.id),
    "awsAccount" -> S(alert.awsAccount),
    "userName" -> S(alert.username),
    "alerts" -> L(alert.alerts.map(alert => M(alertToMap(alert))))
  ))
}

object Dynamo {
  def createId(awsAccount: AwsAccount, username: String) = s"${awsAccount.id}/$username"
}

