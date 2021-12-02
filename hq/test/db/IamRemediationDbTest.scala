package db

import org.scalatest.{FreeSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, PutItemResult, ScanRequest}
import db.IamRemediationDb.{N, S, deserialiseIamRemediationActivity, lookupScanRequest, writePutRequest}
import model.{FinalWarning, IamProblem, IamRemediationActivity, IamRemediationActivityType, MissingMfa, PasswordMissingMFA}
import org.joda.time.DateTime
import utils.attempt.AttemptValues
import scala.concurrent.ExecutionContext.Implicits.global


import scala.collection.JavaConverters._


class IamRemediationDbTest extends FreeSpec with Matchers with AttemptValues {

  val dateNotificationSent = new DateTime(2021, 1, 1, 1, 1)
  val problemCreationDate = new DateTime(2021, 2, 2, 2, 2)
  val dateNotificationSentMillis = dateNotificationSent.getMillis
  val problemCreationDateMillis = problemCreationDate.getMillis
  val tableName = "testTable"

  val iamRemediationActivity = IamRemediationActivity(
    "testAccount",
    "testUser",
    dateNotificationSent,
    FinalWarning,
    PasswordMissingMFA,
    problemCreationDate
  )

  val record = Map(
    "id" -> S("testAccount/testUser"),
    "awsAccountId" -> S("testAccount"),
    "username" -> S("testUser"),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S("FinalWarning"),
    "iamProblem" -> S("PasswordMissingMFA"),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  val incompleteRecord = Map(
    "id" -> S("testAccount/testUser"),
    "username" -> S("testUser"),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S("FinalWarning"),
    "iamProblem" -> S("PasswordMissingMFA"),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  "lookupScanRequest" - {
    "creates scan request for correct table name with correct filter" in {
      val username = "test.user"
      val accountId = "account"
      val tableName = "my table"
      val hashKey = s"${username}/${accountId}"

      val result = lookupScanRequest(username, accountId, tableName)
      result.getTableName shouldEqual tableName
      result.getFilterExpression shouldEqual "id = :key"
      result.getExpressionAttributeValues shouldEqual Map(":key" -> new AttributeValue().withS(hashKey)).asJava
    }
  }

  "writePutRequest" - {
    "creates put request for correct table name with correct attribute name and values" in {
      val putItemRequest = writePutRequest(iamRemediationActivity, tableName)

      putItemRequest.getTableName shouldEqual tableName
      putItemRequest.getItem.asScala("id") shouldEqual S("testAccount/testUser")
      putItemRequest.getItem.asScala("awsAccountId") shouldEqual S("testAccount")
      putItemRequest.getItem.asScala("username") shouldEqual S("testUser")
      putItemRequest.getItem.asScala("dateNotificationSent") shouldEqual N(dateNotificationSentMillis)
      putItemRequest.getItem.asScala("iamRemediationActivityType") shouldEqual S("FinalWarning")
      putItemRequest.getItem.asScala("iamProblem") shouldEqual S("PasswordMissingMFA")
      putItemRequest.getItem.asScala("problemCreationDate") shouldEqual N(problemCreationDateMillis)
    }
  }

  "deserialiseIamRemediationActivity" - {
    "Returns a right with a IamRemediationActivity object correctly instantiated from a database record" in {
        deserialiseIamRemediationActivity(record).value shouldEqual iamRemediationActivity
    }

    "Returns left when database record is incomplete" in {
      deserialiseIamRemediationActivity(incompleteRecord).isFailedAttempt shouldBe true
    }
  }
}
