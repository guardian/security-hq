package db

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, PutItemResult, ScanRequest}
import model.iamremediation.IamRemediationActivity
import model.{AwsAccount, IAMUser}
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.collection.JavaConverters._


class IamRemediationDb(client: AmazonDynamoDB, tableName: String) {
  /**
    * Searches for all notification activity for this IAM user.
    * The application can then filter it down to relevant notifications.
    */
  def lookupIamUserNotifications(IAMUser: IAMUser, awsAccount: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[IamRemediationActivity]] = {
    ???
  }

  /**
    * Writes this record to the database, returning the successful DynamoDB request ID or a failure.
    */
  def writeRemediationActivity(iamRemediationActivity: IamRemediationActivity): Attempt[String] = {
    ???
  }

  private def scan(request: ScanRequest)(implicit ec: ExecutionContext): Attempt[List[Map[String, AttributeValue]]] = {
    ???
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

  private def S(str: String) = new AttributeValue().withS(str)
  private def L(list: List[AttributeValue]) = new AttributeValue().withL(list.asJava)
  private def N(number: Long) = new AttributeValue().withN(number.toString)
  private def N(number: Double) = new AttributeValue().withN(number.toString)
  private def B(boolean: Boolean) = new AttributeValue().withBOOL(boolean)
  private def M(map: Map[String,  AttributeValue]) = new AttributeValue().withM(map.asJava)
}

object IamRemediationDb {
  private[db] def lookupScanRequest(username: String, accountId: String, tableName: String): ScanRequest = {
    ???
  }

  private[db] def writePutRequest(iamRemediationActivity: IamRemediationActivity, tableName: String): PutItemRequest = {
    ???
  }

  /**
    * Attempts to deserialise a database query result into our case class.
    */
  private[db] def deserialiseIamRemediationActivity(dbData: Map[String, AttributeValue]): Attempt[IamRemediationActivity] = {
    ???
  }
}
