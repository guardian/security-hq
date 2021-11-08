package schedule

import aws.AWS
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.{AttributeDefinition, KeySchemaElement, KeyType, ScalarAttributeType}
import config.Config
import model._
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

import scala.collection.JavaConverters._

class AwsDynamoAlertServiceTest extends FreeSpec with BeforeAndAfterEach with BeforeAndAfterAll with Matchers with AttributeValues {

  private val stage = TEST
  val expectedTableName = "security-hq-iam-TEST"
  val securityCredentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials("dummy", "credentials"))
  private val client = AWS.dynamoDbClient(securityCredentialsProvider, Regions.EU_WEST_1, stage)

  // Always reset our dynamo state before and after each test, so every test starts with a blank slate
  override def beforeEach () {
    deleteTestTable()
  }

  override def afterEach () {
    deleteTestTable()
  }

  def deleteTestTable(): Unit = {
    if( client.listTables().getTableNames.contains(expectedTableName) )
      client.deleteTable(expectedTableName)
  }

  "Dynamo" - {
    "initTable method" - {
      "creates a table with the correct name and properties" in {
        AwsDynamoAlertService.initTable(client, stage)

        val tableDescription = client.describeTable(expectedTableName).getTable
        tableDescription.getAttributeDefinitions.asScala.toList shouldEqual List(new AttributeDefinition("id", ScalarAttributeType.S))
        tableDescription.getKeySchema.asScala.toList shouldEqual  List(new KeySchemaElement("id", KeyType.HASH))
        tableDescription.getProvisionedThroughput.getReadCapacityUnits shouldEqual 5
        tableDescription.getProvisionedThroughput.getWriteCapacityUnits shouldEqual 5
        tableDescription.getTableName shouldEqual expectedTableName
      }

      "only creates a table when one is missing" in {
        val initialNumberOfTables = client.listTables().getTableNames.size
        val expectedNumberOfTables = initialNumberOfTables + 1

        AwsDynamoAlertService.initTable(client, stage)
        AwsDynamoAlertService.initTable(client, stage)

        client.listTables().getTableNames.size() shouldEqual expectedNumberOfTables
      }

    }

    "scan method" -  {
      "can scan an empty table for alerts" in {
        val dynamo = AwsDynamoAlertService.initTable(client, stage)
        dynamo.scanAlert() shouldEqual Seq.empty
      }

      "can scan a non-empty table for alerts" in {
        val dynamo = AwsDynamoAlertService.initTable(client, stage)
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
        client.deleteItem(expectedTableName, Map(("id", S(iamAuditUser.id))).asJava)
      }
    }

    "put and get methods" - {
      "can write and read multiple alerts" in {
        val dynamo = AwsDynamoAlertService.initTable(client, stage)
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
