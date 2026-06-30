package db

import db.IamRemediationDb.{deserialiseIamRemediationActivity, lookupScanRequest, writePutRequest}
import model._
import org.joda.time.DateTime
import utils.attempt.{Attempt, Failure}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

class IamRemediationDb(client: DynamoDbClient) {
  /**
    * Searches for all notification activity for this IAM user.
    * The application can then filter it down to relevant notifications.
    */
  def lookupIamUserNotifications(IAMUser: IAMUser, awsAccount: AwsAccount, tableName: String)(implicit ec: ExecutionContext): Attempt[List[IamRemediationActivity]] = {
    for {
      result <- scan(lookupScanRequest(IAMUser.username, awsAccount.id, tableName))
      activities <- Attempt.traverse(result)(deserialiseIamRemediationActivity)
    } yield activities
  }

  /**
    * Writes this record to the database, returning the successful DynamoDB request ID or a failure.
    */
  def writeRemediationActivity(iamRemediationActivity: IamRemediationActivity, tableName: String)(implicit ec: ExecutionContext): Attempt[String] = {
    for {
      result <- put(writePutRequest(iamRemediationActivity, tableName))
    } yield result.responseMetadata.requestId
  }

  private def scan(request: ScanRequest)(implicit ec: ExecutionContext): Attempt[List[Map[String, AttributeValue]]] = {
    try {
      Attempt.Right(client.scan(request).items.asScala.toList.map(_.asScala.toMap))
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          Failure(
            s"unable to scan dynamoDB table",
            s"I haven't been able to scan the dynamo table for the vulnerable user job",
            500,
            throwable = Some(e)
          )
        )
    }
  }

  private def get(request: GetItemRequest)(implicit ec: ExecutionContext): Attempt[Map[String, AttributeValue]] = {
    try {
      Attempt.Right(client.getItem(request).item.asScala.toMap)
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          Failure(
            s"unable to get item from dynamoDB table",
            s"I haven't been able to get the item you were looking for from the dynamo table for the vulnerable user job",
            500,
            throwable = Some(e)
          )
        )
    }
  }

  private def put(request: PutItemRequest)(implicit ec: ExecutionContext): Attempt[PutItemResponse] = {
    try {
      Attempt.Right(client.putItem(request))
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          Failure(s"unable to put item to dynamoDB table",
            s"I haven't been able to put the item into the dynamo table for the vulnerable user job",
            500,
            throwable = Some(e)
          )
        )
    }
  }

}

object IamRemediationDb {
  private[db] def S(str: String) = AttributeValue.builder.s(str).build()
  private[db] def L(list: List[AttributeValue]) = AttributeValue.builder.l(list.asJava).build()
  private[db] def N(number: Long) = AttributeValue.builder.n(number.toString).build()
  private[db] def N(number: Double) = AttributeValue.builder.n(number.toString).build()
  private[db] def B(boolean: Boolean) = AttributeValue.builder.bool(boolean).build()
  private[db] def M(map: Map[String,  AttributeValue]) = AttributeValue.builder.m(map.asJava).build()

  private[db] def lookupScanRequest(username: String, accountId: String, tableName: String): ScanRequest = {
    ScanRequest.builder.tableName(tableName)
      .filterExpression("id = :key")
      .expressionAttributeValues(Map(":key" -> S(s"${accountId}/${username}")).asJava)
      .build()
  }

  private[db] def writePutRequest(iamRemediationActivity: IamRemediationActivity, tableName: String): PutItemRequest = {
    val awsAcountId = iamRemediationActivity.awsAccountId
    val username = iamRemediationActivity.username
    val dateNotificationSent = iamRemediationActivity.dateNotificationSent
    val iamRemediationActivityType = iamRemediationActivity.iamRemediationActivityType
    val iamProblem = iamRemediationActivity.iamProblem
    val problemCreationDate = iamRemediationActivity.problemCreationDate

    val iamRemediationActivityTypeString = iamRemediationActivityType match {
      case Warning => "Warning"
      case FinalWarning => "FinalWarning"
      case Remediation => "Remediation"
    }

    val iamProblemString = iamProblem match {
      case OutdatedCredential => "OutdatedCredential"
    }

    val item = Map(
      "id" -> S(s"${awsAcountId}/${username}"),
      "awsAccountId" -> S(awsAcountId),
      "username" -> S(username),
      "dateNotificationSent" -> N(dateNotificationSent.getMillis),
      "iamRemediationActivityType" -> S(iamRemediationActivityTypeString),
      "iamProblem" -> S(iamProblemString),
      "problemCreationDate" -> N(problemCreationDate.getMillis)
    )

    PutItemRequest.builder.tableName(tableName).item(item.asJava).build()
  }

  /**
    * Attempts to deserialise a database query result into our case class.
    */
  private[db] def deserialiseIamRemediationActivity(dbData: Map[String, AttributeValue])(implicit ec: ExecutionContext): Attempt[IamRemediationActivity] = {
    for {
      awsAccountId <- valueFromDbData(dbData, "awsAccountId", _.s)
      username <- valueFromDbData(dbData, "username", _.s)
      dateNotificationSent <- valueFromDbData(dbData, "dateNotificationSent", _.n.toLong)
      iamRemediationActivityTypeString <- valueFromDbData(dbData, "iamRemediationActivityType", _.s)
      iamRemediationActivity <- iamRemediationActivityFromString(iamRemediationActivityTypeString)
      iamProblemString <- valueFromDbData(dbData, "iamProblem", _.s)
      iamProblem <- iamProblemFromString(iamProblemString)
      problemCreationDate <- valueFromDbData(dbData, "problemCreationDate", _.n.toLong)
    } yield {
      IamRemediationActivity(awsAccountId,
        username,
        new DateTime(dateNotificationSent),
        iamRemediationActivity,
        iamProblem,
        new DateTime(problemCreationDate))
    }
  }

  private def valueFromDbData[A](dbData: Map[String, AttributeValue], key: String, f: AttributeValue => A): Attempt[A] =
    Attempt.fromOption(
      dbData.get(key).flatMap(data => Option(f(data))),
      Failure(
        s"The item retrieved from the database with id ${dbData.get("id")} has an invalid attribute with key $key",
        s"Failed to deserialise database item into an IamRemediationActivity object",
        500,
        None).attempt
    )

  def iamRemediationActivityFromString(str: String): Attempt[IamRemediationActivityType] = {
    str match {
      case "Warning" => Attempt.Right(Warning)
      case "FinalWarning" => Attempt.Right(FinalWarning)
      case "Remediation" => Attempt.Right(Remediation)
      case _ => Attempt.Left {
        Failure(s"Could not turn $str to a IamRemediationActivityType", "Did not understand database record", 500).attempt
      }
    }
  }

  def iamProblemFromString(str: String): Attempt[IamProblem] = {
    str match {
      case "OutdatedCredential" => Attempt.Right(OutdatedCredential)
      case _ => Attempt.Left {
        Failure(s"Could not turn $str to an IamProblem", "Did not understand database record", 500).attempt
      }
    }
  }
}
