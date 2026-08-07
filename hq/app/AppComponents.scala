import aws.ec2.EC2
import aws.{AWS, AwsClient}
import config.CoreConfig.securityCredentialsProvider
import config.{Config, CoreConfig}
import controllers.*
import filters.HstsFilter
import model.AwsAccount
import play.api.ApplicationLoader.Context
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logging}
import play.filters.csrf.CSRFComponents
import router.Routes
import services.{CacheService, MetricService}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ssm.SsmClient
import utils.attempt.Attempt

import scala.concurrent.Await
import scala.concurrent.duration.*
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

  private val regionsNotInSdk: Set[String] = availableRegions
    .map(_.id)
    .toSet -- Region.regions.asScala.map(_.id).toSet
  if (regionsNotInSdk.nonEmpty) {
    logger.warn(
      s"Regions exist that are not in the current SDK (${regionsNotInSdk.mkString(", ")}), update your SDK!"
    )
  }

  private val awsAccounts = Config.getAwsAccounts(configuration)
  private val taClients = AWS.taClients(awsAccounts)
  private val s3Clients = AWS.s3Clients(awsAccounts, availableRegions)
  private val iamClients = AWS.iamClients(awsAccounts)

  private val securitySsmClient = SsmClient.builder
    .credentialsProvider(securityCredentialsProvider)
    .region(CoreConfig.region)
    .build()
  private val googleAuthConfig =
    Config.googleSettings(stage, stack, configuration, securitySsmClient)

  private val cacheService = new CacheService(
    configuration,
    applicationLifecycle,
    environment,
    taClients,
    s3Clients,
    iamClients
  )

  new MetricService(
    applicationLifecycle,
    environment,
    cacheService
  )

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration, googleAuthConfig),
    new AuthController(environment, configuration, googleAuthConfig),
    assets
  )
}
