package utils.attempt

import play.api.Logger
import play.api.mvc.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}

object PlayIntegration extends Results {
  def attempt[A](action: => Attempt[Result])(implicit ec: ExecutionContext): Future[Result] = {
    action.fold(
      { err =>
        Logger.error(err.logString)
        Status(err.statusCode)(views.html.error(err))
      },
      identity
    )
  }
}
