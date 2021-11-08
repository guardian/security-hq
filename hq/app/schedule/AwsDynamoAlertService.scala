package schedule

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import model.{AwsAccount, IamAuditAlert, IamAuditNotificationType, IamAuditUser, Stage}
import org.joda.time.DateTime
import play.api.Logging

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

trait AttributeValues {
  def S(str: String) = new AttributeValue().withS(str)
  def L(list: List[AttributeValue]) = new AttributeValue().withL(list.asJava)
  def N(number: Long) = new AttributeValue().withN(number.toString)
  def N(number: Double) = new AttributeValue().withN(number.toString)
  def B(boolean: Boolean) = new AttributeValue().withBOOL(boolean)
  def M(map: Map[String,  AttributeValue]) = new AttributeValue().withM(map.asJava)
}
trait DynamoAlertService {
  def scanAlert(): Seq[IamAuditUser]
  def getAlert(awsAccount: AwsAccount, username: String): Option[IamAuditUser]
  def putAlert(alert: IamAuditUser): Unit
}

class AwsDynamoAlertService(client: AmazonDynamoDB, stage: Stage) extends DynamoAlertService with AttributeValues with Logging {
  def table = s"security-hq-iam-$stage"

  def initTable(): Unit = {
    createTableIfDoesNotExist()
  }

  private def createTableIfDoesNotExist(): Unit = {
    if (Try(client.describeTable(table)).isFailure) {
      logger.info(s"Creating Dynamo table $table ...")
      createTable(table)
      waitForTableToBecomeActive(table)
    } else {
      logger.info(s"Found Dynamo table $table")
    }
  }

  def createTable(name: String): Unit = {
    val createTableRequest = new CreateTableRequest()
      .withAttributeDefinitions(new AttributeDefinition("id", ScalarAttributeType.S))
      .withTableName(name)
      .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
      .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))

    client.createTable(createTableRequest)
  }

  @tailrec
  private def waitForTableToBecomeActive(name: String): Unit = {
    Try(Option(client.describeTable(name).getTable)).toOption.flatten match {
      case Some(table) if table.getTableStatus == TableStatus.ACTIVE.toString => ()
      case _ =>
        logger.info(s"Waiting for table $name to become active ...")
        Thread.sleep(500L)
        waitForTableToBecomeActive(name)
    }
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
    val key = Map("id" -> S(DynamoAlerts.createId(awsAccount, username)))
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

object DynamoAlerts {
  def createId(awsAccount: AwsAccount, username: String) = s"${awsAccount.id}/$username"
}

