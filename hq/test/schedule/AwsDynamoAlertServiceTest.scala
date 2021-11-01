package schedule

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model._
import logic.AttributeValues
import model._
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.collection.JavaConverters._


class AwsDynamoAlertServiceTest extends FreeSpec with BeforeAndAfterAll with Matchers with AttributeValues {
//
//  private val client = {
//    val conf = new EndpointConfiguration("http://localhost:8000", config.Config.region.name)
//    AmazonDynamoDBClientBuilder.standard
//      .withEndpointConfiguration(conf)
//      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("dummy", "credentials")))
//      .build
//  }
//
//  private val tableName = "security-hq-iam-TEST"
//  private val tableAttributes = Seq((Symbol("id"), ScalarAttributeType.S))
//  private val keySchemaAttributes = tableAttributes
//  private val arbitraryThroughputThatIsIgnoredByDynamoDBLocal = new ProvisionedThroughput(1L, 1L)
//
//  override def beforeAll () {
//    def attributeDefinitions(attributes: Seq[(Symbol, ScalarAttributeType)]) = {
//      attributes.map{ case (symbol, attributeType) => new AttributeDefinition(symbol.name, attributeType)}.asJava
//    }
//
//    def keySchema(attributes: Seq[(Symbol, ScalarAttributeType)]) = {
//      val hashKeyWithType :: rangeKeyWithType = attributes.toList
//      val keySchemas = hashKeyWithType._1 -> KeyType.HASH :: rangeKeyWithType.map(_._1 -> KeyType.RANGE)
//      keySchemas.map{ case (symbol, keyType) => new KeySchemaElement(symbol.name, keyType)}.asJava
//    }
//
//    client.createTable(
//      attributeDefinitions(tableAttributes),
//      tableName,
//      keySchema(keySchemaAttributes),
//      arbitraryThroughputThatIsIgnoredByDynamoDBLocal
//    )
//  }
//
//  override def afterAll () {
//    client.deleteTable(tableName)
//  }
//
//  "Dynamo" - {
//    val dynamo = new AwsDynamoAlertService(client, Some(tableName))
//
//    "scan method" -  {
//      "can scan an empty table for alerts" in {
//        dynamo.scanAlert() shouldEqual Seq.empty
//      }
//
//      "can scan a non-empty table for alerts" in {
//        val iamAuditUser = IamAuditUser(
//          "accountid/username",
//          "accountid",
//          "username",
//          List(
//            IamAuditAlert(VulnerableCredential, DateTime.now(), DateTime.now())
//          )
//        )
//        dynamo.putAlert(iamAuditUser)
//
//        dynamo.scanAlert().size shouldBe 1
//        dynamo.scanAlert().headOption shouldBe Some(iamAuditUser)
//
//        // clean up
//        client.deleteItem(tableName, Map(("id", S(iamAuditUser.id))).asJava)
//      }
//    }
//
//    "put and get methods" - {
//      "can write and read multiple alerts" in {
//        val iamAuditUserVulnerable = IamAuditUser(
//          "accountid/username1",
//          "accountid",
//          "username1",
//          List(
//            IamAuditAlert(VulnerableCredential, DateTime.now(), DateTime.now())
//          )
//        )
//        dynamo.putAlert(iamAuditUserVulnerable)
//        dynamo.scanAlert().size shouldBe 1
//
//        val maybeAlertVulnerable = dynamo.getAlert(AwsAccount("accountid", "name", "arn", "number"), "username1")
//        maybeAlertVulnerable shouldBe Some(iamAuditUserVulnerable)
//
//        val iamAuditUserUnrecognised = IamAuditUser(
//          "accountid/username2",
//          "accountid",
//          "username2",
//          List(
//            IamAuditAlert(UnrecognisedHumanUser, DateTime.now(), DateTime.now())
//          )
//        )
//        dynamo.putAlert(iamAuditUserUnrecognised)
//
//        dynamo.scanAlert().size shouldBe 2
//
//        val maybeAlertUnrecognised = dynamo.getAlert(AwsAccount("accountid", "name", "arn", "number"), "username2")
//        maybeAlertUnrecognised shouldBe Some(iamAuditUserUnrecognised)
//      }
//    }
//  }
//

}
