package db

import org.scalatest.{FreeSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import db.IamRemediationDb.{N, S, deserialiseIamRemediationActivity, lookupScanRequest, writePutRequest}
import model.{FinalWarning, IamRemediationActivity, PasswordMissingMFA}
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
  val activityType = "FinalWarning"
  val problem = "PasswordMissingMFA"

  val iamRemediationActivity = IamRemediationActivity(
    accountId,
    testUser,
    dateNotificationSent,
    FinalWarning,
    PasswordMissingMFA,
    problemCreationDate
  )

  val iamRemediationActivityDbRecord = Map(
    "id" -> S(hashKey),
    "awsAccountId" -> S(accountId),
    "username" -> S(testUser),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S(activityType),
    "iamProblem" -> S(problem),
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
        'getItem (iamRemediationActivityDbRecord.asJava)
      )
    }
  }

  "deserialiseIamRemediationActivity" - {
    "Returns a right with a IamRemediationActivity object correctly instantiated from a database record" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord
      ).value shouldEqual iamRemediationActivity
    }

    "Returns left when database record is incomplete" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord - ("awsAccountId")
      ).isFailedAttempt shouldBe true
    }

    "Returns left when database record is complete but one of the attributes is null" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord + ("username" -> S(null))
      ).isFailedAttempt shouldBe true
    }

    "Returns left when database record is complete but iamProblem is an invalid string" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord + ("iamProblem" -> S("FailFast"))
      ).isFailedAttempt shouldBe true
    }

    "Returns left when database record is complete but one attribute is of the wrong type" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord + ("username" -> N(0))
      ).isFailedAttempt shouldBe true
    }
  }
}
