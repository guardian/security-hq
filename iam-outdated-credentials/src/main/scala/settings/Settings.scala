package settings

import model.{DEV, PROD, Stage}
import software.amazon.awssdk.regions.Region

/** Runtime configuration for the iam-outdated-credentials lambda, sourced from environment variables set by the CDK
  * stack.
  *
  */
case class Settings(
    stack: String,
    stage: Stage,
    dryRun: Boolean,
    anghammaradSnsArn: String,
    iamDynamoTableName: String,
    iamUnrecognisedUserS3Bucket: String,
    iamUnrecognisedUserS3Key: String,
    allowedAccountIds: List[String],
    accountIdsForIamRemediationService: List[String],
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
      dryRun = sys.env.getOrElse("DRY_RUN", "true").toBoolean,
      stack = required("STACK"),
      stage = if (required("STAGE").equalsIgnoreCase("prod")) PROD else DEV,
      anghammaradSnsArn = required("ANGHAMMARAD_SNS_TOPIC_ARN"),
      iamDynamoTableName = required("IAM_DYNAMO_TABLE_NAME"),
      iamUnrecognisedUserS3Bucket = required("IAM_UNRECOGNISED_USER_S3_BUCKET"),
      iamUnrecognisedUserS3Key = required("IAM_UNRECOGNISED_USER_S3_KEY"),
      allowedAccountIds = required("ALLOWED_ACCOUNT_IDS").split(",").toList,
      accountIdsForIamRemediationService = required("ACCOUNT_IDS_FOR_IAM_REMEDIATION_SERVICE").split(",").toList,
      configBucket = required("CONFIG_BUCKET"),
      configKey = required("CONFIG_KEY")
    )
  }
}
