import aws.AWS
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.gu.configraun.Configraun
import com.gu.configraun.aws.AWSSimpleSystemsManagementFactory
import com.gu.configraun.models._
import config.Config
import controllers._
import filters.HstsFilter
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, Logger}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.routing.Router
import play.filters.csrf.CSRFComponents
import router.Routes
import services.CacheService

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
  implicit val awsClient: AWSSimpleSystemsManagement = AWSSimpleSystemsManagementFactory("eu-west-1", "security")

  val configraun: Configuration = {

    configuration.getOptional[String]("stage") match {
      case Some("DEV") =>
        val stack = configuration.get[String]("stack")
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

  private val googleAuthConfig = Config.googleSettings(httpConfiguration, configuration)
  private val inspectorClients = AWS.inspectorClients(configuration)
  private val ec2Clients = AWS.ec2Clients(configuration)
  private val cfnClients = AWS.cfnClients(configuration)
  private val taClients = AWS.taClients(configuration)
  private val iamClients = AWS.iamClients(configuration)

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
    iamClients)

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration, cacheService, googleAuthConfig),
    new SecurityGroupsController(configuration, cacheService, googleAuthConfig),
    new SnykController(configuration, cacheService, googleAuthConfig),
    new InspectorController(configuration, cacheService, googleAuthConfig),
    new AuthController(environment, configuration, googleAuthConfig),
    new UtilityController(),
    assets
  )
}
