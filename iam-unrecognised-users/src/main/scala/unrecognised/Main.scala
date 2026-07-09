package unrecognised

/** Local entrypoint for running the unrecognised-users job outside lambda.
  *
  * `DRY_RUN` is hardcoded to `true` here so that running locally can never accidentally deactivate real IAM users or send
  * real notifications, regardless of what is set in the surrounding environment.
  *
  * `restrictToAccountId` is hardcoded to only poll the `security` account, since local credentials cannot easily assume
  * the cross-account roles needed to poll every other configured account in production.
  */
@main def runUnrecognisedUsers(): Unit =
  UnrecognisedUsers.run(
    env = sys.env + ("DRY_RUN" -> "true"),
    restrictToAccountId = Some("security")
  )
