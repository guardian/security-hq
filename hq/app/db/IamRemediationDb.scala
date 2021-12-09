package db

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, PutItemResult, ScanRequest}
import config.Config.getIamDynamoTableName
import db.IamRemediationDb.{deserialiseIamRemediationActivity, lookupScanRequest, writePutRequest}
import model.{AwsAccount, FinalWarning, IAMUser, IamProblem, IamRemediationActivity, IamRemediationActivityType, OutdatedCredential, PasswordMissingMFA, Remediation, Warning}
import org.joda.time.DateTime
import play.api.Configuration
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.collection.JavaConverters._


class IamRemediationDb(client: AmazonDynamoDB) {
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
    } yield result.getSdkResponseMetadata.getRequestId
  }

  private def scan(request: ScanRequest)(implicit ec: ExecutionContext): Attempt[List[Map[String, AttributeValue]]] = {
    try {
      Attempt.Right(client.scan(request).getItems.asScala.toList.map(_.asScala.toMap))
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
      Attempt.Right(client.getItem(request).getItem.asScala.toMap)
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

  private def put(request: PutItemRequest)(implicit ec: ExecutionContext): Attempt[PutItemResult] = {
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
  private[db] def S(str: String) = new AttributeValue().withS(str)
  private[db] def L(list: List[AttributeValue]) = new AttributeValue().withL(list.asJava)
  private[db] def N(number: Long) = new AttributeValue().withN(number.toString)
  private[db] def N(number: Double) = new AttributeValue().withN(number.toString)
  private[db] def B(boolean: Boolean) = new AttributeValue().withBOOL(boolean)
  private[db] def M(map: Map[String,  AttributeValue]) = new AttributeValue().withM(map.asJava)

  private[db] def lookupScanRequest(username: String, accountId: String, tableName: String): ScanRequest = {
    new ScanRequest().withTableName(tableName)
      .withFilterExpression("id = :key")
      .withExpressionAttributeValues(Map(":key" -> S(s"${accountId}/${username}")).asJava)
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
      case PasswordMissingMFA => "PasswordMissingMFA"
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

    new PutItemRequest().withTableName(tableName).withItem(item.asJava)
  }

  /**
    * Attempts to deserialise a database query result into our case class.
    */
  private[db] def deserialiseIamRemediationActivity(dbData: Map[String, AttributeValue])(implicit ec: ExecutionContext): Attempt[IamRemediationActivity] = {
    for {
      awsAccountId <- valueFromDbData(dbData, "awsAccountId", _.getS)
      username <- valueFromDbData(dbData, "username", _.getS)
      dateNotificationSent <- valueFromDbData(dbData, "dateNotificationSent", _.getN.toLong)
      iamRemediationActivityTypeString <- valueFromDbData(dbData, "iamRemediationActivityType", _.getS)
      iamRemediationActivity <- iamRemediationActivityFromString(iamRemediationActivityTypeString)
      iamProblemString <- valueFromDbData(dbData, "iamProblem", _.getS)
      iamProblem <- iamProblemFromString(iamProblemString)
      problemCreationDate <- valueFromDbData(dbData, "problemCreationDate", _.getN.toLong)
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
      case "PasswordMissingMFA" => Attempt.Right(PasswordMissingMFA)
      case _ => Attempt.Left {
        Failure(s"Could not turn $str to an IamProblem", "Did not understand database record", 500).attempt
      }
    }
  }
}
