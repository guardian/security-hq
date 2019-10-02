package utils.attempt

import controllers.AssetsFinder
import play.api.Logger
import play.api.mvc.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}

object PlayIntegration extends Results {
  def attempt[A](action: => Attempt[Result])(implicit ec: ExecutionContext, assetsFinder: AssetsFinder): Future[Result] = {
    action.fold(
      { failures =>
        Logger("attempt").error(failures.logMessage, failures.firstException.orNull)
        Status(failures.statusCode)(views.html.error(failures))
      },
      identity
    )
  }
}
