package aws

import com.typesafe.scalalogging.LazyLogging
import utils.attempt.{Attempt, Failure}

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future, Promise}

object AwsAsyncHandler extends LazyLogging {
  private val ServiceName = ".*Service: ([^;]+);.*".r
  private val Expired = "The security token included in the request is expired".r
  private val NoCredentials = "Unable to load AWS credentials from any provider in the chain".r
  private val NotAuthorized = "not authorized to perform".r
  private val RateExceeded = "Rate exceeded".r

  def asScala[T](cf: CompletableFuture[T]): Future[T] = {
    val p = Promise[T]()
    cf.whenCompleteAsync { (result, ex) =>
      if (result == null) p failure ex
      else p success result
    }
    p.future
  }

  def handleAWSErrs[T, Client](
      awsClient: AwsClient[Client]
  )(f: => Future[T])(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFuture(f) { case e =>
      val maybeString = Option(e.getMessage)

      val serviceNameOpt = maybeString match {
        case Some(ServiceName(serviceName)) => Some(serviceName)
        case _                              => None
      }

      maybeString match {
        case Some(Expired()) =>
          logger.info(s"Handled ${e.getClass.getSimpleName} exception by string matching: ${e.getMessage}", e)
          Failure.expiredCredentials(serviceNameOpt, awsClient).attempt
        case Some(NoCredentials()) =>
          logger.info(s"Handled ${e.getClass.getSimpleName} exception by string matching: ${e.getMessage}", e)
          Failure.noCredentials(serviceNameOpt, awsClient).attempt
        case Some(NotAuthorized()) =>
          logger.info(s"Handled ${e.getClass.getSimpleName} exception by string matching: ${e.getMessage}", e)
          Failure.insufficientPermissions(serviceNameOpt, awsClient).attempt
        case Some(RateExceeded()) =>
          logger.info(s"Handled ${e.getClass.getSimpleName} exception by string matching: ${e.getMessage}", e)
          Failure.rateLimitExceeded(serviceNameOpt, awsClient).attempt
        case _ =>
          Failure.awsError(serviceNameOpt, awsClient, e).attempt
      }
    }
  }
}
