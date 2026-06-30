package aws

import utils.attempt.{Attempt, Failure}

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future, Promise}

object AwsAsyncHandler {
  private val ServiceName = ".*Service: ([^;]+);.*".r

  def asScala[T](cf: CompletableFuture[T]): Future[T] = {
    val p = Promise[T]()                                                                                                                                           
    cf.whenCompleteAsync{ (result, ex) =>                                                                                                                          
      if (result == null) p failure ex                                                                                                                             
      else                p success result                                                                                                                         
    }                                                                                                                                                              
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
        Failure.awsError(serviceNameOpt, awsClient, e).attempt
      }
    }
  }
}
