package schedule

import aws.AWS
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model.{AttributeDefinition, KeySchemaElement, KeyType, ScalarAttributeType}
import model._
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}
import utils.attempt.AttemptValues
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global


class AwsDynamoAlertServiceTest extends FreeSpec with AttemptValues with BeforeAndAfterEach with BeforeAndAfterAll with Matchers with AttributeValues {

  private val stage = TEST
  val tableName = "security-hq-iam-TEST"
  val prodTableName = "security-hq-iam-PROD"
  val securityCredentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials("security-hq-local-dynamo", "credentials"))
  private val client = AWS.dynamoDbClient(securityCredentialsProvider, Regions.EU_WEST_1, stage)

  // Always reset our dynamo state before and after each test, so every test starts with a blank slate
  override def beforeEach () {
    deleteTestTable()
  }

  override def afterEach () {
    deleteTestTable()
  }

  def deleteTestTable(): Unit = {
    if( client.listTables().getTableNames.contains(tableName) )
      client.deleteTable(tableName)
  }

  "Dynamo" - {
    "init method" - {
      "returns a valid AwsDynamoAlertService without creating a table when running in PROD" in {
        a [ResourceNotFoundException] should be thrownBy client.describeTable(prodTableName)

        AwsDynamoAlertService.init(client, PROD, Some(prodTableName)) should be ('right)

        a [ResourceNotFoundException] should be thrownBy client.describeTable(prodTableName)
      }

      "requires specifying the table name when running in PROD, but not in local development" in {
        AwsDynamoAlertService.init(client, PROD, None) should be ('left)
        AwsDynamoAlertService.init(client, PROD, Some(prodTableName)) should be ('right)

        AwsDynamoAlertService.init(client, DEV, None) should be ('right)
        AwsDynamoAlertService.init(client, TEST, None) should be ('right)
      }

      "creates the necessary table and returns a valid AwsDynamoAlertService when running locally" in {
        a [ResourceNotFoundException] should be thrownBy client.describeTable(tableName)

        AwsDynamoAlertService.init(client, stage, None) should be ('right)

        noException should be thrownBy client.describeTable(tableName)
      }

      "is idempotent - can be executed multiple times without changing the initial result or failing" in {
        AwsDynamoAlertService.init(client, stage, None) should be ('right)
        AwsDynamoAlertService.init(client, stage, None) should be ('right)
      }
    }

    "scan method" -  {
      "can scan an empty table for alerts" in {
        val dynamo = AwsDynamoAlertService.init(client, stage, None).right.get
        dynamo.scanAlert() shouldEqual Seq.empty
      }

      "can scan a non-empty table for alerts" in {
        val dynamo = AwsDynamoAlertService.init(client, stage, None).right.get
        val iamAuditUser = IamAuditUser(
          "accountid/username",
          "accountid",
          "username",
          List(
            IamAuditAlert(VulnerableCredential, DateTime.now(), DateTime.now())
          )
        )
        dynamo.putAlert(iamAuditUser)

        dynamo.scanAlert().size shouldBe 1
        dynamo.scanAlert().headOption shouldBe Some(iamAuditUser)

        // clean up
        client.deleteItem(tableName, Map(("id", S(iamAuditUser.id))).asJava)
      }
    }

    "put and get methods" - {
      "can write and read multiple alerts" in {
        val dynamo = AwsDynamoAlertService.init(client, stage, None).right.get
        val iamAuditUserVulnerable = IamAuditUser(
          "accountid/username1",
          "accountid",
          "username1",
          List(
            IamAuditAlert(VulnerableCredential, DateTime.now(), DateTime.now())
          )
        )
        dynamo.putAlert(iamAuditUserVulnerable)
        dynamo.scanAlert().size shouldBe 1

        val maybeAlertVulnerable = dynamo.getAlert(AwsAccount("accountid", "name", "arn", "number"), "username1")
        maybeAlertVulnerable shouldBe Some(iamAuditUserVulnerable)

        val iamAuditUserUnrecognised = IamAuditUser(
          "accountid/username2",
          "accountid",
          "username2",
          List(
            IamAuditAlert(UnrecognisedHumanUser, DateTime.now(), DateTime.now())
          )
        )
        dynamo.putAlert(iamAuditUserUnrecognised)

        dynamo.scanAlert().size shouldBe 2

        val maybeAlertUnrecognised = dynamo.getAlert(AwsAccount("accountid", "name", "arn", "number"), "username2")
        maybeAlertUnrecognised shouldBe Some(iamAuditUserUnrecognised)
      }
    }
  }
}
