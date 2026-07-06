package logic

import settings.Settings
import utils.attempt.Attempt

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

object IamOutdatedCredentialsMain {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = try {
    val settings = Settings.fromArgs(args)

    val result: Attempt[Unit] = IamOutdatedCredentials.disableOutdatedCredentials(settings)

    Await.result(result.underlying, 10.minutes) match {
      case Right(_) =>
        println("Completed successfully")

      case Left(err) =>
        Console.err.println(s"Job failed: $err")
        sys.exit(1)
    }
  } catch {
    case _: IllegalArgumentException =>
      println(
        """
        |Usage: sbt 'iamOutdatedCredentials / run
        |  <stack>
        |  <stage>
        |  <dryRunFlag>
        |  <configBucket>
        |  <configKey>
        |'""".stripMargin
      )
      sys.exit(1)
  }
}
