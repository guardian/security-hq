package config

import aws.AwsClient
import aws.ec2.EC2
import model.{AwsAccount, DEV, PROD, Stage}
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import utils.attempt.Attempt

import java.net.URI
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

/** Configuration values shared by `core` and `hq`. */
object CoreConfig {
  val iamHumanUserRotationCadence: Long = 90
  val iamMachineUserRotationCadence: Long = 365
  val outdatedCredentialOptOutUserTag = "SecurityHQ::OutdatedCredentialOptOut"
  val daysBetweenWarningAndFinalNotification = 7
  val daysBetweenFinalNotificationAndRemediation = 7

  // TODO fetch the region dynamically from the instance
  val region: Region = Region.of("eu-west-1")

  def calculateAvailableRegions(stack: String, stage: Stage)(implicit ec: ExecutionContext): List[Region] = {

    val ec2Client = AwsClient(
      Ec2AsyncClient.builder
        .region(CoreConfig.region)
        .build(),
      AwsAccount(stack, stack, stack, stack),
      CoreConfig.region
    )
    try {
      val availableRegionsAttempt: Attempt[List[Region]] = for {
        ec2RegionList <- EC2.getAvailableRegions(ec2Client)
        regionList = ec2RegionList.map(ec2Region => Region.of(ec2Region.regionName))
      } yield regionList
      Await
        .result(availableRegionsAttempt.asFuture, 30 seconds)
        .getOrElse(List(CoreConfig.region, Region.of("us-east-1")))
    } finally {
      ec2Client.client.close()
    }

  }

  val securityCredentialsProvider: AwsCredentialsProviderChain = AwsCredentialsProviderChain.of(
    ProfileCredentialsProvider.create("security"),
    DefaultCredentialsProvider.builder.build()
  )


  def getSecurityDynamoDbClient(stage: Stage) = {
    stage match {
      case PROD =>
        DynamoDbClient
          .builder()
          .credentialsProvider(securityCredentialsProvider)
          .region(CoreConfig.region)
          .build()
      case DEV =>
        DynamoDbClient
          .builder()
          .credentialsProvider(securityCredentialsProvider)
          .region(CoreConfig.region)
          .endpointOverride(
            new URI("http://localhost:8000")
          ) // An alternative could be to configure a specific builder DynamoDbEndpointParams.builder().endpoint("http://localhost:8000").region(Config.region)
          .build()
    }
  }

}
