package config

import aws.AwsClient
import aws.ec2.EC2
import com.typesafe.config.{Config, ConfigFactory}
import model.{AwsAccount, DEV, PROD, Stage}
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  DefaultCredentialsProvider,
  ProfileCredentialsProvider
}
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import utils.attempt.Attempt

import java.net.URI
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.ListHasAsScala

/** Configuration values shared by `core` and `hq`. */
object CoreConfig {
  val iamHumanUserRotationCadence: Long = 90
  val iamMachineUserRotationCadence: Long = 365
  val outdatedCredentialOptOutUserTag = "SecurityHQ::OutdatedCredentialOptOut"
  val daysBetweenWarningAndFinalNotification = 7
  val daysBetweenFinalNotificationAndRemediation = 7

  // TODO fetch the region dynamically from the instance
  val region: Region = Region.of("eu-west-1")

  def calculateAvailableRegions(stack: String, stage: Stage)(implicit ec: ExecutionContext): Attempt[List[Region]] = {
    val ec2Client = AwsClient(
      Ec2AsyncClient.builder
        .region(CoreConfig.region)
        .build(),
      // This account name is the only useful element (in logging contextString)
      AwsAccount("n/a", stack, "n/a", "n/a"),
      CoreConfig.region
    )
    (for {
      ec2RegionList <- EC2.getAvailableRegions(ec2Client)
      regionList = ec2RegionList.map(ec2Region => Region.of(ec2Region.regionName))
    } yield regionList)
      .tap(_ => ec2Client.client.close())
  }

  val securityCredentialsProvider: AwsCredentialsProviderChain = AwsCredentialsProviderChain.of(
    ProfileCredentialsProvider.create("security"),
    DefaultCredentialsProvider.builder.build()
  )

  def getSecurityDynamoDbClient(stage: Stage): DynamoDbClient = {
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

  def loadConfigFromS3(bucket: String, key: String, s3: S3Client): Config = {
    val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
    val configString: String = s3.getObject(request, ResponseTransformer.toBytes()).asUtf8String()
    ConfigFactory.parseString(configString).resolve()
  }

  def parseAccounts(config: Config): List[AwsAccount] = {
    config.getConfigList(AWS_ACCOUNTS_CONFIG_ITEM).asScala.toList.map { accountConfig =>
      AwsAccount(
        id = accountConfig.getString("id"),
        name = accountConfig.getString("name"),
        roleArn = accountConfig.getString("roleArn"),
        accountNumber = accountConfig.getString("number")
      )
    }
  }
  private val AWS_ACCOUNTS_CONFIG_ITEM = "AWS_ACCOUNTS"

}
