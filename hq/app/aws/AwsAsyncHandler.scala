package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import play.api.Logging
import utils.attempt.{Attempt, Failure}

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
  def serviceNameOpt(e: Throwable): Option[String] = e.getMessage match {
    case ServiceName(serviceName) => Some(serviceName)
    case _ => None
  }

  def awsToScala[R <: AmazonWebServiceRequest, T, Client](client: AwsClient[Client])(sdkMethod: Client => ( (R, AsyncHandler[R, T]) => java.util.concurrent.Future[T])): (R => Future[T]) = { req =>
    val p = Promise[T]
    sdkMethod(client.client)(req, new AwsAsyncPromiseHandler(p, client))
    p.future
  }

  def recoverAWSErrs[T, Client](
    awsClient: AwsClient[Client],
    recover: PartialFunction[Throwable, T]
  )(f: => Future[T])(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFutureWithAcceptableFailure(f)({
      case e if e.getMessage.contains("The security token included in the request is expired") =>
        Failure.expiredCredentials(serviceNameOpt(e), awsClient).attempt
      case e if e.getMessage.contains("Unable to load AWS credentials from any provider in the chain") =>
        Failure.noCredentials(serviceNameOpt(e), awsClient).attempt
      case e if e.getMessage.contains("not authorized to perform") =>
        Failure.insufficientPermissions(serviceNameOpt(e), awsClient).attempt
      case e if e.getMessage.contains("Rate exceeded") =>
        Failure.rateLimitExceeded(serviceNameOpt(e), awsClient).attempt
      case e => Failure.awsError(serviceNameOpt(e), awsClient).attempt
    }, recover)
  }


  def handleAWSErrs[T, Client](awsClient: AwsClient[Client])(f: => Future[T])(implicit ec: ExecutionContext): Attempt[T] =
    recoverAWSErrs(awsClient, PartialFunction.empty)(f)

}
