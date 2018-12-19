package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import play.api.Logger
import utils.attempt.{Attempt, Failure}

import scala.concurrent.{ExecutionContext, Future, Promise}


class AwsAsyncPromiseHandler[R <: AmazonWebServiceRequest, T](promise: Promise[T], clientContext: AwsClient[_]) extends AsyncHandler[R, T] {
  def onError(e: Exception): Unit = {
    val context = Failure.contextString(clientContext)
    Logger.warn(s"Failed to execute AWS SDK operation, $context", e)
    promise failure e
  }
  def onSuccess(r: R, t: T): Unit = {
    promise success t
  }
}

object AwsAsyncHandler {
  private val ServiceName = ".*Service: ([^;]+);.*".r
  def awsToScala[R <: AmazonWebServiceRequest, T, Client](client: AwsClient[Client])(sdkMethod: Client => ( (R, AsyncHandler[R, T]) => java.util.concurrent.Future[T])): (R => Future[T]) = { req =>
    val p = Promise[T]
    sdkMethod(client.client)(req, new AwsAsyncPromiseHandler(p, client))
    p.future
  }

  def handleAWSErrs[T, Client](awsClient: AwsClient[Client])(f: => Future[T])(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFuture(f) { case e =>
      val serviceNameOpt = e.getMessage match {
        case ServiceName(serviceName) => Some(serviceName)
        case _ => None
      }
      if (e.getMessage.contains("The security token included in the request is expired")) {
        Failure.expiredCredentials(serviceNameOpt, awsClient).attempt
      } else if (e.getMessage.contains("Unable to load AWS credentials from any provider in the chain")) {
        Failure.noCredentials(serviceNameOpt, awsClient).attempt
      } else if (e.getMessage.contains("not authorized to perform")) {
        Failure.insufficientPermissions(serviceNameOpt, awsClient).attempt
      } else if (e.getMessage.contains("Rate exceeded")) {
        Failure.rateLimitExceeded(serviceNameOpt, awsClient).attempt
      } else {
        Failure.awsError(serviceNameOpt, awsClient).attempt
      }
    }
  }
}
