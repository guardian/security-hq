import aws.{AWS, AwsClient}
import aws.ec2.EC2
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.gu.configraun.Configraun
import com.gu.configraun.aws.AWSSimpleSystemsManagementFactory
import com.gu.configraun.models._
import config.Config
import controllers._
import filters.HstsFilter
import model.AwsAccount
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, Logger}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.routing.Router
import play.filters.csrf.CSRFComponents
import router.Routes
import services.CacheService
import utils.attempt.Attempt

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
  with CSRFComponents
  with AhcWSComponents with AssetsComponents {

  implicit val impWsClient: WSClient = wsClient
  implicit val impPlayBodyParser: BodyParser[AnyContent] = playBodyParsers.default
  implicit val impControllerComponents: ControllerComponents = controllerComponents
  implicit val impAssetsFinder: AssetsFinder = assetsFinder
  override lazy val httpFilters = Seq(
    csrfFilter,
    new HstsFilter()
  )
  private val region = Regions.EU_WEST_1
  private val stack = configuration.get[String]("stack")
  implicit val awsClient: AWSSimpleSystemsManagement = AWSSimpleSystemsManagementFactory(region.getName, stack)

  val configraun: Configuration = {

    configuration.getOptional[String]("stage") match {
      case Some("DEV") =>
        val app = configuration.get[String]("app")
        val stage = "DEV"
        Configraun.loadConfig(Identifier(Stack(stack), App(app), Stage.fromString(stage).get)) match {
          case Left(a) =>
            Logger.error(s"Unable to load Configraun configuration from AWS (${a.message})")
            sys.exit(1)
          case Right(a) => a
        }
      case _ => Configraun.loadConfig match {
        case Left(a) =>
          Logger.error(s"Unable to load Configraun configuration from AWS tags (${a.message})")
          sys.exit(1)
        case Right(a: com.gu.configraun.models.Configuration) => a
      }
    }
  }

  // the aim of this is to get a list of available regions that we are able to access
  // note that:
  //  - Regions.values() returns Chinese and US government regions that are not accessible with the same AWS account
  //  - available regions can return regions that are not in the SDK and so Regions.findName will fail
  // to solve these we return the intersection of available regions and regions.values()
  private val availableRegions = {
    val ec2Client = AwsClient(AmazonEC2AsyncClientBuilder.standard().withRegion(region).build(), AwsAccount(stack, stack, stack), region)
    try {
      val availableRegionsAttempt: Attempt[List[Regions]] = for {
        regionList <- EC2.getAvailableRegions(ec2Client)
        regionStringSet = regionList.map(_.getRegionName).toSet
      } yield Regions.values.filter(r => regionStringSet.contains(r.getName)).toList
      Await.result(availableRegionsAttempt.asFuture, 30 seconds).right.get
    } finally {
      ec2Client.client.shutdown()
    }
  }

  Logger.info(s"Polling in the following regions: ${availableRegions.map(_.getName).mkString(", ")}")

  val regionsNotInSdk: Set[String] = availableRegions.map(_.getName).toSet -- Regions.values().map(_.getName).toSet
  if (regionsNotInSdk.nonEmpty) {
    Logger.warn(s"Regions exist that are not in the current SDK (${regionsNotInSdk.mkString(", ")}), update your SDK!")
  }

  private val googleAuthConfig = Config.googleSettings(httpConfiguration, configuration)
  private val inspectorClients = AWS.inspectorClients(configuration)
  private val ec2Clients = AWS.ec2Clients(configuration, availableRegions)
  private val cfnClients = AWS.cfnClients(configuration, availableRegions)
  private val taClients = AWS.taClients(configuration)
  private val s3Clients = AWS.s3Clients(configuration, availableRegions)
  private val iamClients = AWS.iamClients(configuration, availableRegions)

  private val cacheService = new CacheService(
    configuration,
    applicationLifecycle,
    environment,
    configraun,
    wsClient,
    inspectorClients,
    ec2Clients,
    cfnClients,
    taClients,
    s3Clients,
    iamClients,
    availableRegions)

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration, googleAuthConfig),
    new CredentialsController(configuration, cacheService, googleAuthConfig),
    new BucketsController(configuration, cacheService, googleAuthConfig),
    new SecurityGroupsController(configuration, cacheService, googleAuthConfig),
    new SnykController(configuration, cacheService, googleAuthConfig),
    new InspectorController(configuration, cacheService, googleAuthConfig),
    new AuthController(environment, configuration, googleAuthConfig),
    assets
  )
}
