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
  implicit val playBodyParser: BodyParser[AnyContent] = playBodyParsers.default
  implicit val components: ControllerComponents = controllerComponents

  override lazy val httpFilters = Seq(
    csrfFilter,
    new HstsFilter()
  )

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration),
    new SecurityGroupsController(configuration),
    new AuthController(environment, configuration),
    new UtilityController(),
    assets
  )
}
