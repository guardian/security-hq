package db

import org.scalatest.{FreeSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, PutItemResult, ScanRequest}
import db.IamRemediationDb.{N, S, deserialiseIamRemediationActivity, lookupScanRequest, writePutRequest}
import model.{AccessKey, AwsAccount, FinalWarning, Green, HumanUser, IamProblem, IamRemediationActivity, IamRemediationActivityType, MissingMfa, NoKey, PasswordMissingMFA}
import org.joda.time.DateTime
import utils.attempt.AttemptValues

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._


class IamRemediationDbTest extends FreeSpec with Matchers with AttemptValues {

  val tableName = "testTable"
  val hashKey = "testAccountId/testUser"
  val accountId = "testAccountId"
  val testUser = "testUser"
  val dateNotificationSent = new DateTime(2021, 1, 1, 1, 1)
  val dateNotificationSentMillis = dateNotificationSent.getMillis
  val problemCreationDate = new DateTime(2021, 2, 2, 2, 2)
  val problemCreationDateMillis = problemCreationDate.getMillis

  val iamRemediationActivity = IamRemediationActivity(
    accountId,
    testUser,
    dateNotificationSent,
    FinalWarning,
    PasswordMissingMFA,
    problemCreationDate
  )

  val iamRemediationActivityMap = Map(
    "id" -> S(hashKey),
    "awsAccountId" -> S(accountId),
    "username" -> S(testUser),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S("FinalWarning"),
    "iamProblem" -> S("PasswordMissingMFA"),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  val incompleteMap = Map(
    "id" -> S(hashKey),
    "username" -> S(testUser),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S("FinalWarning"),
    "iamProblem" -> S("PasswordMissingMFA"),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  "lookupScanRequest" - {
    "creates scan request for correct table name with correct filter" in {
      lookupScanRequest(testUser, accountId, tableName) should have(
        'tableName (tableName),
        'filterExpression ("id = :key"),
        'expressionAttributeValues (Map(":key" -> new AttributeValue().withS(hashKey)).asJava)
      )
    }
  }

  "writePutRequest" - {
    "creates put request for correct table name with correct attribute name and values" in {
      writePutRequest(iamRemediationActivity, tableName) should have(
        'tableName (tableName),
        'getItem (iamRemediationActivityMap.asJava)
      )
    }
  }

  "deserialiseIamRemediationActivity" - {
    "Returns a right with a IamRemediationActivity object correctly instantiated from a database record" in {
        deserialiseIamRemediationActivity(iamRemediationActivityMap).value shouldEqual iamRemediationActivity
    }

    "Returns left when database record is incomplete" in {
      deserialiseIamRemediationActivity(incompleteMap).isFailedAttempt shouldBe true
    }
  }
}
