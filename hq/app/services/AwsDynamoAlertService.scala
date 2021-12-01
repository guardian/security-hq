package services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, PutItemResult}
import model._
import org.joda.time.DateTime
import play.api.Logging
import services.AwsDynamoAlertService._
import utils.attempt.{Attempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal


/**
  * This class wraps up the logic for communicating with DynamoDB,
  * including converting to and from DynamoDB's internal types.
  *
  * @param client Authenticated AWS SDK client for Database operations
  * @param tableName The name of the table used to store the audit user information
  */
class AwsDynamoAlertService(client: AmazonDynamoDB, tableName: String) extends Logging {
  /**
    * Attempts to look up information for the provided user. If we do not have
    * an entry for the user yet this will return None, which is expected behaviour.
    * This just means that the vulnerability is new to us (we haven't done any alerting yet).
    *
    * // TODO: This currently returns None for all failures, instead of just "not found"
    */
  def lookupVulnerableUser(vulnerableUser: VulnerableUser, awsAccount: AwsAccount)(implicit ec: ExecutionContext): Attempt[Option[IamAuditUser]] = {
    val request = getRequest(vulnerableUser, awsAccount, tableName)
    for {
      data <- get(request)
    } yield keyToIamAuditUser(data)
  }

  /**
    * Add or update a user to the database. This will be done after each operation SHQ performs
    */
  def putIamAuditUser(iamAuditUser: IamAuditUser)(implicit ec: ExecutionContext): Attempt[String] = {
    val request = putRequest(iamAuditUser, tableName)
    for {
      response <- put(request)
    } yield response.getSdkResponseMetadata.getRequestId
  }


  // Underlying database operations

  private def get(request: GetItemRequest)(implicit ec: ExecutionContext): Attempt[Map[String, AttributeValue]] = {
    try {
      Attempt.Right(client.getItem(request).getItem.asScala.toMap)
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          Failure(s"unable to get item from dynamoDB table",
            s"I haven't been able to get the item you were looking for from the dynamo table for the vulnerable user job",
            500,
            context = Some(e.getMessage),
            throwable = Some(e)
          )
        )
    }
  }

  private def put(request: PutItemRequest)(implicit ec: ExecutionContext): Attempt[PutItemResult] = {
    try {
      Attempt.Right(client.putItem(request))
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          Failure(s"unable to put item to dynamoDB table",
            s"I haven't been able to put the item into the dynamo table for the vulnerable user job",
            500,
            context = Some(e.getMessage),
            throwable = Some(e)
          )
        )
    }
  }
}
object AwsDynamoAlertService {
  // unmarshall database response
  private[services] def keyToIamAuditUser(dbData: Map[String, AttributeValue]): Option[IamAuditUser] = {
    for {
      alerts <- Option(dbData("alerts").getL).map(_.asScala.toList)
      // attempt to read IamAuditAlert instances from the alerts field
      iamAuditAlerts = alerts.flatMap { a =>
        for {
          alertMap <- Option(a.getM).map(_.asScala)
          alertType <- Option(alertMap("type").getS)
          notificationType <- alertType match {
            case "vulnerableCredential" =>
              Some(VulnerableCredential)
            case "unrecognisedHumanUser" =>
              Some(UnrecognisedHumanUser)
            case _ =>
              None
          }
          alertDate <- Option(alertMap("date").getN).map(_.toLong)
          alertDisableDeadline <- Option(alertMap("disableDeadline").getN).map(_.toLong)
        } yield IamAuditAlert(
          notificationType,
          new DateTime(alertDate),
          new DateTime(alertDisableDeadline)
        )
      }
      id <- Option(dbData("id").getS)
      account <- Option(dbData("awsAccount").getS)
      username <- Option(dbData("username").getS)
    } yield IamAuditUser(id, account, username, iamAuditAlerts)
  }

  // DyanamoDB requests

  private[services] def getRequest(vulnerableUser: VulnerableUser, awsAccount: AwsAccount, tableName: String): GetItemRequest = {
    new GetItemRequest()
      .withTableName(tableName)
      .withKey(
        Map("id" -> S(s"${awsAccount.id}/${vulnerableUser.username}")).asJava
      )
  }

  private[services] def putRequest(user: IamAuditUser, tableName: String): PutItemRequest = {
    val alerts = user.alerts.map { alert =>
      M(Map(
        "type" -> S(alert.`type`.name),
        "date" -> N(alert.dateNotificationSent.getMillis),
        "disableDeadline" -> N(alert.disableDeadline.getMillis)
      ))
    }
    val item = Map(
      "id" -> S(user.id),
      "awsAccount" -> S(user.awsAccount),
      "username" -> S(user.username),
      "alerts" -> L(alerts)
    )
    new PutItemRequest()
      .withTableName(tableName)
      .withItem(item.asJava)
  }

  // DynamoDB attribute helpers

  private def S(str: String) = new AttributeValue().withS(str)
  private def L(list: List[AttributeValue]) = new AttributeValue().withL(list.asJava)
  private def N(number: Long) = new AttributeValue().withN(number.toString)
  private def N(number: Double) = new AttributeValue().withN(number.toString)
  private def B(boolean: Boolean) = new AttributeValue().withBOOL(boolean)
  private def M(map: Map[String,  AttributeValue]) = new AttributeValue().withM(map.asJava)
}
