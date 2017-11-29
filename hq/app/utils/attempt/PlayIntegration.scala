package utils.attempt

import controllers.AssetsFinder
import play.api.Logger
import play.api.mvc.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}

object PlayIntegration extends Results {
  def attempt[A](action: => Attempt[Result])(implicit ec: ExecutionContext, assetsFinder: AssetsFinder): Future[Result] = {
    action.fold(
      { err =>
        err.failures.foreach { failure =>
          failure.throwable match {
            case Some(th) => Logger.error(failure.message, th)
            case _ => Logger.error(failure.message)
          }
        }
        Status(err.statusCode)(views.html.error(err))
      },
      identity
    )
  }
}
