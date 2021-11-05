package schedule

import aws.AWS
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
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
  private val client = AWS.dynamoDbClient(securityCredentialsProvider, "eu-west-1", stage)

  // Always reset our dynamo' state before each test
  // That way we write the tests can be written on the assumption that dynamo is a blank slate
  override def beforeEach () {
    if( client.listTables().getTableNames.contains(expectedTableName) )
      client.deleteTable(expectedTableName)
    client.listTables().getTableNames should not contain(expectedTableName)
  }

  // Clean up at the end, after all tests have run (regardless of what order they run in)
  override def afterAll () {
    if(client.listTables().getTableNames.contains(expectedTableName))
      client.deleteTable(expectedTableName)
  }

  "Dynamo" - {
    "constructor" - {
      "creates a table with the right name when one is missing" in {
        val currentNumberOfTables = client.listTables().getTableNames.size
        val expectedNumberOfTables = currentNumberOfTables + 1

        new AwsDynamoAlertService(client, stage)
        client.listTables().getTableNames.size() shouldEqual expectedNumberOfTables
        client.listTables().getTableNames should contain(expectedTableName)
      }

      "does not create a table when one already exists" in {
        new AwsDynamoAlertService(client, stage)
        val expectedNumberOfTables = client.listTables().getTableNames.size

        new AwsDynamoAlertService(client, stage)
        client.listTables().getTableNames.size() shouldEqual expectedNumberOfTables
      }

      "creates a table according to the PROD specification" in {
        new AwsDynamoAlertService(client, stage)
        val tableDescription = client.describeTable(expectedTableName).getTable
        tableDescription.getAttributeDefinitions.asScala.toList shouldEqual List(new AttributeDefinition("id", ScalarAttributeType.S))
        tableDescription.getKeySchema.asScala.toList shouldEqual  List(new KeySchemaElement("id", KeyType.HASH))
        tableDescription.getProvisionedThroughput.getReadCapacityUnits shouldEqual 5
        tableDescription.getProvisionedThroughput.getWriteCapacityUnits shouldEqual 5
        tableDescription.getTableName shouldEqual expectedTableName
      }
    }

    "scan method" -  {
      "can scan an empty table for alerts" in {
        val dynamo = new AwsDynamoAlertService(client, stage)
        dynamo.scanAlert() shouldEqual Seq.empty
      }

      "can scan a non-empty table for alerts" in {
        val dynamo = new AwsDynamoAlertService(client, stage)
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
        val dynamo = new AwsDynamoAlertService(client, stage)
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
