package db

import org.scalatest.OptionValues
import org.joda.time.DateTime
import db.IamRemediationDb.{N, S, deserialiseIamRemediationActivity, lookupScanRequest, writePutRequest}
import model.{FinalWarning, IamRemediationActivity, OutdatedCredential}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import utils.attempt.AttemptValues

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class IamRemediationDbTest extends AnyFreeSpec with Matchers with AttemptValues with OptionValues {

  val tableName = "testTable"
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
    OutdatedCredential,
    problemCreationDate
  )

  val iamRemediationActivityDbRecord = Map(
    "id" -> S(s"$accountId/$testUser"),
    "awsAccountId" -> S(accountId),
    "username" -> S(testUser),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S("FinalWarning"),
    "iamProblem" -> S("OutdatedCredential"),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  "lookupScanRequest" - {
    "creates scan request for correct table name" in {
      lookupScanRequest(testUser, accountId, tableName) should have(
        Symbol("tableName") (tableName),
      )
    }

    "creates scan request that filters on record id, which uses aws account id and username" in {
      lookupScanRequest(testUser, accountId, tableName) should have(
        Symbol("filterExpression") ("id = :key"),
        Symbol("expressionAttributeValues") (Map(":key" -> AttributeValue.builder.s(s"$accountId/$testUser").build()).asJava)
      )
    }
  }

  "writePutRequest" - {
    "creates put request for correct table name with correct attribute name and values" in {
      writePutRequest(iamRemediationActivity, tableName) should have(
        Symbol("tableName") (tableName),
        Symbol("item") (iamRemediationActivityDbRecord.asJava)
      )
    }

    "the record id is made up of aws account id and username" in {
      val id = writePutRequest(iamRemediationActivity, tableName).item.asScala.get("id").value
      id.s shouldEqual s"$accountId/$testUser"
    }
  }

  "deserialiseIamRemediationActivity" - {
    "Returns a right with an IamRemediationActivity object correctly instantiated from a database record" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord
      ).value() shouldEqual iamRemediationActivity
    }

    "Returns a left when database record is incomplete" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord - ("awsAccountId")
      ).isFailedAttempt() shouldBe true
    }

    "Returns a left when database record is complete but one of the attributes is null" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord + ("username" -> S(null))
      ).isFailedAttempt() shouldBe true
    }

    "Returns a left when database record is complete but iamProblem is an invalid string" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord + ("iamProblem" -> S("FailFast"))
      ).isFailedAttempt() shouldBe true
    }

    "Returns a left when database record is complete but one attribute is of the wrong type" in {
      deserialiseIamRemediationActivity(
        iamRemediationActivityDbRecord + ("username" -> N(0))
      ).isFailedAttempt() shouldBe true
    }
  }
}
