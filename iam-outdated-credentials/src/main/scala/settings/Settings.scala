package settings

import model.{DEV, PROD, Stage}

/** Runtime configuration for the iam-outdated-credentials lambda, sourced from environment variables set by the CDK
  * stack.
  */
case class Settings(
    stack: String,
    stage: Stage,
    dryRun: Boolean,
    configBucket: String,
    configKey: String
)

object Settings {
  def fromArgs(args: Array[String]): Settings = {
    args.toList match {
      case stack
          :: stageString
          :: dryRunString
          :: configBucket
          :: configKey
          :: Nil =>

        val stage = stageString.toLowerCase match {
          case "prod" => PROD
          case "dev"  => DEV
          case other   => throw new IllegalArgumentException(s"Invalid stage '$other' (expected DEV or PROD)")
        }
        val dryRun = dryRunString.toBooleanOption.getOrElse(
          throw new IllegalArgumentException(s"Invalid dryRun flag '$dryRunString' (expected true/false)")
        )
        Settings(
          stack = stack,
          stage = stage,
          dryRun = dryRun,
          configBucket = configBucket,
          configKey = configKey
        )
      case _ =>
        throw new IllegalArgumentException("Expected args: <stack> <stage> <dryRunFlag> <configBucket> <configKey>")
    }
  }
  def fromEnvironment(): Settings = {
    def required(key: String): String =
      sys.env.getOrElse(
        key,
        throw new RuntimeException(
          s"Missing required environment variable $key"
        )
      )

    Settings(
      dryRun = sys.env.getOrElse(DRY_RUN, "true").toBoolean,
      stack = required(STACK),
      stage = if (required(STAGE).equalsIgnoreCase("prod")) PROD else DEV,
      configBucket = required(CONFIG_BUCKET),
      configKey = required(CONFIG_KEY)
    )
  }

  private val DRY_RUN = "DRY_RUN"
  private val STACK = "STACK"
  private val STAGE = "STAGE"
  private val CONFIG_BUCKET = "CONFIG_BUCKET"
  private val CONFIG_KEY = "CONFIG_KEY"

}
