package com.gu.shq.outdatedcredentials

import com.gu.shq.model.{DEV, PROD, Stage}

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
