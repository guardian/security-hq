import akka.actor.ActorSystem
import aws.ec2.EC2
import aws.iam.IAMClient
import aws.support.TrustedAdvisorExposedIAMKeys
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
import service.{CredentialServiceUpdater, SecurityGroupsUpdater}


class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
  with CSRFComponents
  with AhcWSComponents with AssetsComponents  {

  val sgsUpdater = new SecurityGroupsUpdater(configuration)
  val credUpdater = new CredentialServiceUpdater(configuration)

  implicit val impWsClient: WSClient = wsClient
  implicit val impPlayBodyParser: BodyParser[AnyContent] = playBodyParsers.default
  implicit val impControllerComponents: ControllerComponents = controllerComponents
  implicit val impAssetsFinder: AssetsFinder = assetsFinder
  override lazy val httpFilters = Seq(
    csrfFilter,
    new HstsFilter()
  )

  def startBackgroundServices(actorSystem : ActorSystem) = {
    sgsUpdater.update(actorSystem)(EC2.allFlaggedSgs)
    credUpdater.updateExposedKeys(actorSystem)(TrustedAdvisorExposedIAMKeys.getAllAccountsExposedIAMKeys)
    credUpdater.updateCredentialReport(actorSystem)(IAMClient.getAllCredentialReports)
  }

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration),
    new SecurityGroupsController(configuration),
    new AuthController(environment, configuration),
    new UtilityController(),
    assets
  )
}
