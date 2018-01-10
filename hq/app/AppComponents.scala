import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.gu.configraun.Configraun
import com.gu.configraun.aws.AWSSimpleSystemsManagementFactory
import com.gu.configraun.models.{Configuration, StringParam}
import controllers._
import filters.HstsFilter
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.routing.Router
import play.filters.csrf.CSRFComponents
import router.Routes


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

  implicit val configraun = {

    configuration.getOptional[String]("configSource") match {
      case Some("local") => {
        val token = new StringParam(configuration.get[String]("hq.snyk_token"))
        val organisation = new StringParam(configuration.get[String]("hq.organisation"))
        com.gu.configraun.models.Configuration(Map(("/snyk/token", token), ("/snyk/organisation", organisation)))
      }
      case _ => Configraun.loadConfig match {
        case Left(a) => throw new RuntimeException(s"Unable to load Configraun configuration (${a.message})")
        case Right(a: com.gu.configraun.models.Configuration) => a
      }
    }
  }

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration),
    new SecurityGroupsController(configuration),
    new SnykController(configuration),
    new AuthController(environment, configuration),
    new UtilityController(),
    assets
  )
}
