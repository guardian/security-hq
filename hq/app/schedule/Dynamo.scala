package schedule

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, ScanRequest}
import model.{AwsAccount, IamAuditAlert, IamAuditNotificationType, IamAuditUser}
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

  private val table = tableName match {
    case Some(tableName) => tableName
    case None =>
      logger.error("unable to retrieve Iam Dynamo Table Name from config - check that table name is present in security-hq.conf in S3")
      "error"
  }

  private def scan: Seq[Map[String, AttributeValue]] = {
    try {
      client.scan(new ScanRequest().withTableName(table)).getItems.asScala.map(_.asScala.toMap)
    } catch {
      case NonFatal(e) =>
        logger.error(s"unable to scan dynamoDB table $table: ${e.getMessage}", e)
        List.empty
    }
  }

  def scanAlert(): Seq[IamAuditUser] = {
    scan.map { r =>
      val alerts = r("alerts").getL.asScala.map { a =>
        val alertMap = a.getM.asScala
        IamAuditAlert(
          IamAuditNotificationType.fromName(alertMap("type").getS),
          new DateTime(alertMap("date").getN.toLong),
          new DateTime(alertMap("disableDeadline").getN.toLong)
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

  private def get(key: Map[String, AttributeValue]): Option[Map[String, AttributeValue]] = {
    try {
      Option(client.getItem(
        new GetItemRequest().withTableName(table).withKey(key.asJava)).getItem).map(_.asScala.toMap)
    } catch {
      case NonFatal(e) =>
        logger.error(s"unable to get item from dynamoDB table $table: ${e.getMessage}", e)
        None
    }
  }

  def getAlert(awsAccount: AwsAccount, username: String): Option[IamAuditUser] = {
    logger.info(s"Fetching alert for username ${username}, account ${awsAccount.id}")
    val key = Map("id" -> S(Dynamo.createId(awsAccount, username)))
    get(key).map { r =>
      val alerts = r("alerts").getL.asScala.map{ a =>
        val alertMap = a.getM.asScala
        IamAuditAlert(
          IamAuditNotificationType.fromName(alertMap("type").getS),
          new DateTime(alertMap("date").getN.toLong),
          new DateTime(alertMap("disableDeadline").getN.toLong)
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

  private def put(item: Map[String, AttributeValue]): Unit = try {
    logger.info(s"putting item to dynamoDB table: $table")
    client.putItem(
      new PutItemRequest().withTableName(table).withItem(item.asJava))
  } catch {
    case NonFatal(e) =>
    logger.error(s"unable to put item to dynamoDB table: ${e.getMessage}", e)
  }

  private def alertToMap(e: IamAuditAlert): Map[String, AttributeValue] = {
    Map(
      "type" -> S(e.`type`.name),
      "date" -> N(e.dateNotificationSent.getMillis),
      "disableDeadline" -> N(e.disableDeadline.getMillis)
    )
  }

  def putAlert(alert: IamAuditUser): Unit = put(Map(
    "id" -> S(alert.id),
    "awsAccount" -> S(alert.awsAccount),
    "username" -> S(alert.username),
    "alerts" -> L(alert.alerts.map(alert => M(alertToMap(alert))))
  ))
}

object Dynamo {
  def createId(awsAccount: AwsAccount, username: String) = s"${awsAccount.id}/$username"
}

