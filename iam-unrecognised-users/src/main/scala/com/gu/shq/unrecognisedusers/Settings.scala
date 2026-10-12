package com.gu.shq.unrecognisedusers

import software.amazon.awssdk.regions.Region

import scala.concurrent.duration.FiniteDuration

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
    region: Region,
    timeout: FiniteDuration
)
