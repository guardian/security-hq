package unrecognised

import software.amazon.awssdk.regions.Region

/** Runtime configuration for the iam-unrecognised-users lambda, sourced from environment variables set by the CDK
  * stack.
  *
  *   - `DRY_RUN` (default `true`): when true, unrecognised users are logged but access keys/passwords are not
  *     deactivated and no notifications are sent.
  *   - `CONFIG_BUCKET`: S3 bucket holding `security-hq.conf`.
  *   - `CONFIG_KEY`: S3 key for `security-hq.conf`.
  *   - `IAM_UNRECOGNISED_USER_S3_BUCKET`: S3 bucket holding the Janus data file (the audit-data bucket).
  *   - `IAM_UNRECOGNISED_USER_S3_KEY`: S3 key for the Janus data file.
  *   - `REGION` (default `eu-west-1`): primary region for the owning account clients and config bucket.
  */
case class Settings(
    dryRun: Boolean,
    configBucket: String,
    configKey: String,
    janusBucket: String,
    janusKey: String,
    region: Region
)

object Settings {

  /** @param env
    *   the environment variables to read from; defaults to the process environment, but can be overridden (for example
    *   by local entrypoints or tests) without mutating global state.
    */
  def fromEnvironment(env: Map[String, String] = sys.env): Settings = {
    def required(key: String): String =
      env.getOrElse(
        key,
        throw new RuntimeException(s"Missing required environment variable $key")
      )

    Settings(
      dryRun = env.getOrElse(DRY_RUN, "true").toBoolean,
      configBucket = required(CONFIG_BUCKET),
      configKey = required(CONFIG_KEY),
      janusBucket = required(IAM_UNRECOGNISED_USER_S3_BUCKET),
      janusKey = required(IAM_UNRECOGNISED_USER_S3_KEY),
      region = Region.of(env.getOrElse(REGION, "eu-west-1"))
    )
  }

  private val DRY_RUN = "DRY_RUN"
  private val CONFIG_BUCKET = "CONFIG_BUCKET"
  private val CONFIG_KEY = "CONFIG_KEY"
  private val IAM_UNRECOGNISED_USER_S3_BUCKET = "IAM_UNRECOGNISED_USER_S3_BUCKET"
  private val IAM_UNRECOGNISED_USER_S3_KEY = "IAM_UNRECOGNISED_USER_S3_KEY"
  private val REGION = "REGION"
}
