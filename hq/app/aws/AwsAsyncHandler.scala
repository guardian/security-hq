package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import play.api.Logging
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.{ExecutionContext, Future, Promise}


class AwsAsyncPromiseHandler[R <: AmazonWebServiceRequest, T](promise: Promise[T], clientContext: AwsClient[_]) extends AsyncHandler[R, T] with Logging {
  def onError(e: Exception): Unit = {
    val context = Failure.contextString(clientContext)
    logger.warn(s"Failed to execute AWS SDK operation (${clientContext.client.getClass.getSimpleName}), $context", e)
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

  def handleAWSErrs[T, Client](
    awsClient: AwsClient[Client],
    additionalErrors: PartialFunction[Throwable, FailedAttempt] = PartialFunction.empty
  )(f: => Future[T])(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFuture(f) { case e =>
      val serviceNameOpt = e.getMessage match {
        case ServiceName(serviceName) => Some(serviceName)
        case _ => None
      }
      val globalAwsErrors: PartialFunction[Throwable, FailedAttempt] = {
        case e if e.getMessage.contains("The security token included in the request is expired") =>
          Failure.expiredCredentials(serviceNameOpt, awsClient).attempt
        case e if e.getMessage.contains("Unable to load AWS credentials from any provider in the chain") =>
          Failure.noCredentials(serviceNameOpt, awsClient).attempt
        case e if e.getMessage.contains("not authorized to perform") =>
          Failure.insufficientPermissions(serviceNameOpt, awsClient).attempt
        case e if e.getMessage.contains("Rate exceeded") =>
          Failure.rateLimitExceeded(serviceNameOpt, awsClient).attempt
        case _ => Failure.awsError(serviceNameOpt, awsClient).attempt
      }
      (additionalErrors orElse globalAwsErrors)(e)
    }
  }
}
