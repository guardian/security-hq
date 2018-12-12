package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions
import model.AwsAccount
import play.api.Logger
import utils.attempt.{Attempt, Failure}

import scala.concurrent.{ExecutionContext, Future, Promise}


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
  private val ServiceName = ".*Service: ([^;]+);.*".r
  def awsToScala[R <: AmazonWebServiceRequest, T](sdkMethod: ( (R, AsyncHandler[R, T]) => java.util.concurrent.Future[T])): (R => Future[T]) = { req =>
    val p = Promise[T]
    sdkMethod(req, new AwsAsyncPromiseHandler(p))
    p.future
  }

  def handleAWSErrs[T](account: Option[AwsAccount] = None, region: Option[Regions] = None)(f: Future[T])(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFuture(f) { case e =>
      val serviceNameOpt = e.getMessage match {
        case ServiceName(serviceName) => Some(serviceName)
        case _ => None
      }
      if (e.getMessage.contains("The security token included in the request is expired")) {
        Failure.expiredCredentials(serviceNameOpt, account, region).attempt
      } else if (e.getMessage.contains("Unable to load AWS credentials from any provider in the chain")) {
        Failure.noCredentials(serviceNameOpt, account, region).attempt
      } else if (e.getMessage.contains("not authorized to perform")) {
        Failure.insufficientPermissions(serviceNameOpt, account, region).attempt
      } else if (e.getMessage.contains("Rate exceeded")) {
        Failure.rateLimitExceeded(serviceNameOpt, account, region).attempt
      } else {
        Failure.awsError(serviceNameOpt, account, region).attempt
      }
    }
  }
}
