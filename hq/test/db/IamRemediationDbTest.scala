package db

import org.scalatest.{FreeSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, PutItemResult, ScanRequest}
import db.IamRemediationDb.{lookupScanRequest, writePutRequest}
import model.{FinalWarning, IamProblem, IamRemediationActivity, IamRemediationActivityType, MissingMfa, PasswordMissingMFA}
import org.joda.time.DateTime

import scala.collection.JavaConverters._


class IamRemediationDbTest extends FreeSpec with Matchers {
  def S(str: String) = new AttributeValue().withS(str)
  def L(list: List[AttributeValue]) = new AttributeValue().withL(list.asJava)
  def N(number: Long) = new AttributeValue().withN(number.toString)
  def N(number: Double) = new AttributeValue().withN(number.toString)
  def B(boolean: Boolean) = new AttributeValue().withBOOL(boolean)
  def M(map: Map[String,  AttributeValue]) = new AttributeValue().withM(map.asJava)

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
    "TODO" ignore {}
  }

  "deserialiseIamRemediationActivity" - {
    "TODO" ignore {}
  }
}
