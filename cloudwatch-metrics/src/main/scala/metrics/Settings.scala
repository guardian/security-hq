package metrics

import software.amazon.awssdk.regions.Region

/** Runtime configuration for the cloudwatch-metrics lambda, sourced from
  * environment variables set by the CDK stack.
  *
  *   - `DRY_RUN` (default `true`): when true, metrics are logged but not
  *     written to CloudWatch.
  *   - `CONFIG_BUCKET`: S3 bucket holding `security-hq.conf`.
  *   - `CONFIG_KEY`: S3 key for `security-hq.conf`.
  *   - `REGION` (default `eu-west-1`): primary region for the own-account
  *     clients and config bucket.
  */
case class Settings(
    dryRun: Boolean,
    configBucket: String,
    configKey: String,
    region: Region
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
      dryRun = sys.env.getOrElse("DRY_RUN", "true").toBoolean,
      configBucket = required("CONFIG_BUCKET"),
      configKey = required("CONFIG_KEY"),
      region = Region.of(sys.env.getOrElse("REGION", "eu-west-1"))
    )
  }
}
