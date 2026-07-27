package unrecognised

import scala.concurrent.ExecutionContext.Implicits.global

/** Local entrypoint for running the unrecognised-users job outside lambda.
  *
  * Configuration is supplied by the surrounding environment, for example via `script/start iam-unrecognised-users`.
  *
 * `DRY_RUN` is hardcoded to `true` here so that running locally can never accidentally deactivate real IAM users or
 * send real notifications, regardless of what is set in the surrounding environment.
  */
@main def runUnrecognisedUsers(): Unit =
  UnrecognisedUsers.run(
    env = sys.env + ("DRY_RUN" -> "true")
  )
