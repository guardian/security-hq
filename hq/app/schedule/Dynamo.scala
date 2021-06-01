package schedule

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest}
import model.{IamAuditAlert, IamAuditUser}
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

  def get(key: Map[String, AttributeValue]): Option[Map[String, AttributeValue]] = Option(client.getItem(
    new GetItemRequest().withTableName(table).withKey(key.asJava)).getItem) map (_.asScala.toMap)

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
      "notificationType" -> S(e.notificationType.name)
    )
  }

  def putAlert(alert: IamAuditUser): Unit = put(Map(
    "id" -> S(alert.id),
    "awsAccount" -> S(alert.awsAccount),
    "userName" -> S(alert.userName),
    "alerts" -> L(alert.alerts.map(alert => M(alertToMap(alert))))
  ))
}

