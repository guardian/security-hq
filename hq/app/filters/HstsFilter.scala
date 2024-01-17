package filters

import org.apache.pekko.stream.Materializer
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}


class HstsFilter(implicit material: Materializer, ec: ExecutionContext) extends Filter {
  def apply(next: RequestHeader => Future[Result])(header: RequestHeader): Future[Result] =
    next(header).map(_.withHeaders("Strict-Transport-Security" -> "max-age=31536000"))

  override implicit def mat = material
}
