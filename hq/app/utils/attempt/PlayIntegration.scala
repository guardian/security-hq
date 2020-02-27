package utils.attempt

import controllers.AssetsFinder
import play.api.Logging
import play.api.mvc.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}

object PlayIntegration extends Results with Logging {
  def attempt[A](action: => Attempt[Result])(implicit ec: ExecutionContext, assetsFinder: AssetsFinder): Future[Result] = {
    action.fold(
      { failures =>
        logger.error(failures.logMessage, failures.firstException.orNull)
        Status(failures.statusCode)(views.html.error(failures))
      },
      identity
    )
  }
}
