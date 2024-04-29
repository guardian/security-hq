import aws.ec2.EC2
import aws.{AWS, AwsClient}
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.{Region, RegionUtils, Regions}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder
import com.google.cloud.securitycenter.v1.{SecurityCenterClient, SecurityCenterSettings}
import config.Config
import controllers._
import db.IamRemediationDb
import filters.HstsFilter
import model.{AwsAccount, DEV, PROD}
import play.api.ApplicationLoader.Context
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logging}
import play.filters.csrf.CSRFComponents
import router.Routes
import services.{CacheService, IamRemediationService, MetricService}
import utils.attempt.Attempt

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
  with CSRFComponents
  with AhcWSComponents with AssetsComponents with Logging {

  implicit val impWsClient: WSClient = wsClient
  implicit val impPlayBodyParser: BodyParser[AnyContent] = playBodyParsers.default
  implicit val impControllerComponents: ControllerComponents = controllerComponents
  implicit val impAssetsFinder: AssetsFinder = assetsFinder
  override lazy val httpFilters = Seq(
    csrfFilter,
    new HstsFilter()
  )

  private val stack = configuration.get[String]("stack")
  private val stage = Config.getStage(configuration)

  // the aim of this is to get all the regions that are available to this account
  private val availableRegions: List[Region] = {
    val ec2Client = AwsClient(AmazonEC2AsyncClientBuilder.standard().withRegion(Config.region.getName).build(), AwsAccount(stack, stack, stack, stack), Config.region)
    try {
      val availableRegionsAttempt: Attempt[List[Region]] = for {
        ec2RegionList <- EC2.getAvailableRegions(ec2Client)
        regionList = ec2RegionList.map(ec2Region => RegionUtils.getRegion(ec2Region.getRegionName))
      } yield regionList
      Await.result(availableRegionsAttempt.asFuture, 30 seconds).getOrElse(List(Config.region, RegionUtils.getRegion("us-east-1")))
    } finally {
      ec2Client.client.shutdown()
    }
  }

  logger.info(s"Polling in the following regions: ${availableRegions.map(_.getName).mkString(", ")}")

  val regionsNotInSdk: Set[String] = availableRegions.map(_.getName).toSet -- Regions.values().map(_.getName).toSet
  if (regionsNotInSdk.nonEmpty) {
    logger.warn(s"Regions exist that are not in the current SDK (${regionsNotInSdk.mkString(", ")}), update your SDK!")
  }

  private val snykConfig = Config.getSnykConfig(configuration)
  private val ec2Clients = AWS.ec2Clients(configuration, availableRegions)
  private val cfnClients = AWS.cfnClients(configuration, availableRegions)
  private val taClients = AWS.taClients(configuration)
  private val s3Clients = AWS.s3Clients(configuration, availableRegions)
  private val iamClients = AWS.iamClients(configuration, availableRegions)
  private val efsClients = AWS.efsClients(configuration, availableRegions)

  private val securityCredentialsProvider = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("security"),
    DefaultAWSCredentialsProviderChain.getInstance()
  )
  private val securitySnsClient = AmazonSNSAsyncClientBuilder.standard()
    .withCredentials(securityCredentialsProvider)
    .withRegion(Config.region.getName)
    .withClientConfiguration(new ClientConfiguration().withMaxConnections(10))
    .build()
  private val securitySsmClient = AWSSimpleSystemsManagementClientBuilder.standard()
    .withCredentials(securityCredentialsProvider)
    .withRegion(Config.region.getName)
    .build()
  private val googleAuthConfig = Config.googleSettings(stage, stack, configuration, securitySsmClient)

  private val securityDynamoDbClient = stage match {
    case PROD =>
      AmazonDynamoDBClientBuilder.standard()
        .withCredentials(securityCredentialsProvider)
        .withRegion(Config.region.getName)
        .build()
    case DEV =>
      AmazonDynamoDBClientBuilder.standard()
        .withCredentials(securityCredentialsProvider)
        .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", Config.region.getName))
        .build()
  }
  private val securityS3Client = AmazonS3ClientBuilder.standard()
    .withCredentials(securityCredentialsProvider)
    .withRegion(Config.region.getName)
    .build()

  private val securityCenterSettings = SecurityCenterSettings.newBuilder().setCredentialsProvider(Config.gcpCredentialsProvider(configuration)).build()
  private val securityCenterClient = SecurityCenterClient.create(securityCenterSettings)

  private val cacheService = new CacheService(
    configuration,
    applicationLifecycle,
    environment,
    snykConfig,
    wsClient,
    ec2Clients,
    cfnClients,
    taClients,
    s3Clients,
    iamClients,
    efsClients,
    availableRegions,
    securityCenterClient
  )

  new MetricService(
    configuration,
    applicationLifecycle,
    environment,
    cacheService
  )

  new IamRemediationService(
    cacheService,
    securitySnsClient,
    new IamRemediationDb(securityDynamoDbClient),
    configuration,
    iamClients,
    applicationLifecycle,
    environment,
    securityS3Client
  )(executionContext)

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration, googleAuthConfig),
    new CredentialsController(configuration, cacheService, googleAuthConfig),
    new BucketsController(configuration, cacheService, googleAuthConfig),
    new SecurityGroupsController(configuration, cacheService, googleAuthConfig),
    new SnykController(configuration, cacheService, googleAuthConfig),
    new AuthController(environment, configuration, googleAuthConfig),
    assets,
    new GcpController(configuration, googleAuthConfig, cacheService)
  )
}
