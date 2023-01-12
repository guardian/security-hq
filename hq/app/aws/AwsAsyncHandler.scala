package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import net.logstash.logback.marker.Markers.appendEntries
import play.api.{Logging, MarkerContext}
import utils.attempt.{Attempt, Failure}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._

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

object AwsAsyncHandler extends Logging {
  private val ServiceName = ".*Service: ([^;]+);.*".r
  def awsToScala[R <: AmazonWebServiceRequest, T, Client](client: AwsClient[Client])(sdkMethod: Client => ( (R, AsyncHandler[R, T]) => java.util.concurrent.Future[T])): (R => Future[T]) = { req =>
    val p = Promise[T]()
    sdkMethod(client.client)(req, new AwsAsyncPromiseHandler(p, client))
    p.future
  }

  def handleAWSErrs[T, Client](awsClient: AwsClient[Client])(f: => Future[T])(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFuture(f) { case e =>
      val serviceNameOpt = e.getMessage match {
        case ServiceName(serviceName) => Some(serviceName)
        case _ => None
      }

      val markers = MarkerContext(appendEntries(
        Map(
          "serviceName" -> serviceNameOpt.getOrElse("unknown")
        ).asJava
      ))

      logger.error(s"AWS error: ${e.getMessage}")(markers)

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
