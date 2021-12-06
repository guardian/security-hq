package db

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
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

  val iamRemediationActivityMap = Map(
    "id" -> S(hashKey),
    "awsAccountId" -> S(accountId),
    "username" -> S(testUser),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S(activityType),
    "iamProblem" -> S(problem),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  val incompleteMap = Map(
    "id" -> S(hashKey),
    "username" -> S(testUser),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S(activityType),
    "iamProblem" -> S(problem),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  val nullMap = Map(
    "id" -> S(hashKey),
    "awsAccountId" -> S(accountId),
    "username" -> S(null),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S(activityType),
    "iamProblem" -> S(problem),
    "problemCreationDate" -> N(problemCreationDateMillis)
  )

  val invalidIamProblemMap = Map(
    "id" -> S(hashKey),
    "awsAccountId" -> S(accountId),
    "username" -> S(testUser),
    "dateNotificationSent" -> N(dateNotificationSentMillis),
    "iamRemediationActivityType" -> S(activityType),
    "iamProblem" -> S("FailFast"),
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

    "Returns left when database record is complete but one of the attributes is null" in {
      val ret = deserialiseIamRemediationActivity(nullMap)
      ret.isFailedAttempt shouldBe true
    }

    "Returns left when database record is complete but iamProblem is an invalid string" in {
      val ret = deserialiseIamRemediationActivity(invalidIamProblemMap)
      ret.isFailedAttempt shouldBe true
    }
  }
}
