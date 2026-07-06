package logic

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import settings.Settings

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

class IamOutdatedCredentialsLambda extends RequestHandler[Map[String, String], String] {

  implicit val ec: ExecutionContext = ExecutionContext.global

  override def handleRequest(
      event: Map[String, String],
      context: Context
  ): String = {

    val settings = Settings.fromEnvironment()

    val result = IamOutdatedCredentials.job(settings)

    Await.result(result, 10.minutes) match {
      case Right(_) =>
        "Success"

      case Left(err) =>
        throw new RuntimeException(err.toString)
    }
  }
}
