import aws.ec2.EC2
import aws.{AWS, AwsClient}
import config.CoreConfig.{getSecurityDynamoDbClient, securityCredentialsProvider}
import config.{Config, CoreConfig}
import controllers.*
import db.IamRemediationDb
import filters.HstsFilter
import logic.IamOutdatedCredentials
import model.{AwsAccount, Stage}
import play.api.ApplicationLoader.Context
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logging}
import play.filters.csrf.CSRFComponents
import router.Routes
import services.{CacheService, IamRemediationService, MetricService}
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkAsyncHttpClientBuilder
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.utils.AttributeMap
import utils.attempt.Attempt

import scala.concurrent.duration.*
import scala.concurrent.Await
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with CSRFComponents
    with AhcWSComponents
    with AssetsComponents
    with Logging {

  implicit val impWsClient: WSClient = wsClient
  implicit val impPlayBodyParser: BodyParser[AnyContent] =
    playBodyParsers.default
  implicit val impControllerComponents: ControllerComponents =
    controllerComponents
  implicit val impAssetsFinder: AssetsFinder = assetsFinder
  override lazy val httpFilters = Seq(
    csrfFilter,
    new HstsFilter()
  )

  private val stack = configuration.get[String]("stack")
  private val stage = Config.getStage(configuration)

  // the aim of this is to get all the regions that are available to this account
  // TODO: Delete this and only use calculateAvailableRegions which does not await.
  private val availableRegions: List[Region] = {
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
        // This is not correct - the lambda rewrite will not do this.
        .getOrElse(List(CoreConfig.region, Region.of("us-east-1")))
    } finally {
      ec2Client.client.close()
    }
  }

  logger.info(
    s"Polling in the following regions: ${availableRegions.map(_.id).mkString(", ")}"
  )

  val regionsNotInSdk: Set[String] = availableRegions
    .map(_.id)
    .toSet -- Region.regions.asScala.map(_.id).toSet
  if (regionsNotInSdk.nonEmpty) {
    logger.warn(
      s"Regions exist that are not in the current SDK (${regionsNotInSdk.mkString(", ")}), update your SDK!"
    )
  }

  private val awsAccounts = Config.getAwsAccounts(configuration)
  private val cfnClients = AWS.cfnClients(awsAccounts, availableRegions)
  private val taClients = AWS.taClients(awsAccounts)
  private val s3Clients = AWS.s3Clients(awsAccounts, availableRegions)
  private val iamClients = AWS.iamClients(awsAccounts, availableRegions)
  private val devXSecurityAccountMaybe = awsAccounts.find(_.id == IamOutdatedCredentials.SECURITY_ACCOUNT_ID)

  /*
      The casting from SdkHttpConfigurationOption[Integer] to AttributeMap.Key[Any] is required because Scala compiler comlains

      Integer <: Any, but Java-defined class Key is invariant in type T.
      You may wish to investigate a wildcard type such as `_ <: Any`. (SLS 3.2.10)
   */
  private val MAX_10_CONNECTIONS: AttributeMap = AttributeMap
    .builder()
    .put(SdkHttpConfigurationOption.MAX_CONNECTIONS.asInstanceOf[AttributeMap.Key[Any]], 10)
    .build()

  private val securitySnsClient = SnsAsyncClient.builder
    .credentialsProvider(securityCredentialsProvider)
    .region(CoreConfig.region)
    .httpClient(new DefaultSdkAsyncHttpClientBuilder().buildWithDefaults(MAX_10_CONNECTIONS))
    .build()
  private val securitySsmClient = SsmClient.builder
    .credentialsProvider(securityCredentialsProvider)
    .region(CoreConfig.region)
    .build()
  private val googleAuthConfig =
    Config.googleSettings(stage, stack, configuration, securitySsmClient)

  private val securityDynamoDbClient = getSecurityDynamoDbClient(stage: Stage)

  private val securityS3Client = S3Client.builder
    .credentialsProvider(securityCredentialsProvider)
    .region(CoreConfig.region)
    .build()

  private val cacheService = new CacheService(
    configuration,
    applicationLifecycle,
    environment,
    cfnClients,
    taClients,
    s3Clients,
    iamClients,
    availableRegions
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
    securityS3Client,
    devXSecurityAccountMaybe
  )(executionContext)

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration, googleAuthConfig),
    new AuthController(environment, configuration, googleAuthConfig),
    assets
  )
}
