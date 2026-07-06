package settings

import model.{DEV, PROD, Stage}

/** Runtime configuration for the iam-outdated-credentials lambda, sourced from environment variables set by the CDK
  * stack.
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
  def fromArgs(args: Array[String]): Settings = {
    args.toList match {
      case stack
          :: stageString
          :: dryRunString
          :: anghammaradSnsArn
          :: iamDynamoTableName
          :: iamUnrecognisedUserS3Bucket
          :: iamUnrecognisedUserS3Key
          :: allowedAccountIds
          :: accountIdsForIamRemediationService
          :: configBucket
          :: configKey
          :: Nil =>

        val stage = if (stageString.equalsIgnoreCase("prod")) PROD else DEV
        val dryRun = dryRunString.equalsIgnoreCase("true")
        Settings(
          stack = stack,
          stage = stage,
          dryRun = dryRun,
          anghammaradSnsArn = anghammaradSnsArn,
          iamDynamoTableName = iamDynamoTableName,
          iamUnrecognisedUserS3Bucket = iamUnrecognisedUserS3Bucket,
          iamUnrecognisedUserS3Key = iamUnrecognisedUserS3Key,
          allowedAccountIds = allowedAccountIds.split(",").toList,
          accountIdsForIamRemediationService = accountIdsForIamRemediationService.split(",").toList,
          configBucket = configBucket,
          configKey = configKey
        )
      case _ =>
        println(
          "Usage: sbt 'project iam-outdated-credentials; runMain logic/IamOutdatedCredentialsMain " +
            "   <stack>" +
            "   <stage>" +
            "   <dryRunFlag>" +
            "   <anghammaradSnsArn>" +
            "   <tableName>" +
            "   <accountId>" +
            "   <accountName>" +
            "   <roleArn>" +
            "   <accountNumber>'"
        )
        throw new RuntimeException("Invalid arguments")
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
