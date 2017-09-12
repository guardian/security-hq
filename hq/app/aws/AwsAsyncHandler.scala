package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import play.api.Logger

import scala.concurrent.{Future, Promise}


class AwsAsyncPromiseHandler[R <: AmazonWebServiceRequest, T](promise: Promise[T]) extends AsyncHandler[R, T] {
  def onError(e: Exception): Unit = {
    Logger.warn("Failed to execute AWS SDK operation", e)
    promise failure e
  }
  def onSuccess(r: R, t: T): Unit = {
    promise success t
  }
}

object AwsAsyncHandler {
  def awsToScala[R <: AmazonWebServiceRequest, T](sdkMethod: Function2[R, AsyncHandler[R, T], java.util.concurrent.Future[T]]): Function1[R, Future[T]] = { req =>
    val p = Promise[T]
    sdkMethod(req, new AwsAsyncPromiseHandler(p))
    p.future
  }
}
