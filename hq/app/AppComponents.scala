import controllers._
import filters.HstsFilter
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import router.Routes
import play.filters.csrf.CSRFComponents


class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
  with CSRFComponents
  with AhcWSComponents {

  implicit val ec = play.api.libs.concurrent.Execution.Implicits.defaultContext

  override lazy val httpFilters = Seq(
    csrfFilter,
    new HstsFilter()
  )

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration),
    new Assets(httpErrorHandler)
  )
}
