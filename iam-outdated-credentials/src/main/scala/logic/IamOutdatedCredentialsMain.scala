package logic

import settings.Settings

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

object IamOutdatedCredentialsMain {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    val settings = Settings.fromArgs(args)

    val result = IamOutdatedCredentials.job(settings)

    Await.result(result, 10.minutes) match {
      case Right(_) =>
        println("Completed successfully")

      case Left(err) =>
        Console.err.println(s"Job failed: $err")
        sys.exit(1)
    }
  }

}
