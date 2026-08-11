import { GuScheduledLambda } from "@guardian/cdk";
import { GuAlarm } from "@guardian/cdk/lib/constructs/cloudwatch";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import {
  GuAnghammaradTopicParameter,
  GuDistributionBucketParameter,
  GuParameter,
  GuStack,
  GuStringParameter,
} from "@guardian/cdk/lib/constructs/core";
import type { AppIdentity } from "@guardian/cdk/lib/constructs/core/identity";
import { GuDynamoTable } from "@guardian/cdk/lib/constructs/dynamodb";
import {
  GuAllowPolicy,
  GuDynamoDBReadPolicy,
  GuDynamoDBWritePolicy,
  GuGetS3ObjectsPolicy,
  GuPolicy,
  GuPutCloudwatchMetricsPolicy,
} from "@guardian/cdk/lib/constructs/iam";
import { GuAnghammaradSenderPolicy } from "@guardian/cdk/lib/constructs/iam/policies/anghammarad";
import { GuDeveloperPolicyExperimental } from "@guardian/cdk/lib/experimental/constructs/iam/policies";
import type { App } from "aws-cdk-lib";
import { Duration, RemovalPolicy } from "aws-cdk-lib";
import {
  ComparisonOperator,
  Metric,
  TreatMissingData,
} from "aws-cdk-lib/aws-cloudwatch";
import { AttributeType } from "aws-cdk-lib/aws-dynamodb";
import { Schedule } from "aws-cdk-lib/aws-events";
import { Effect, PolicyStatement } from "aws-cdk-lib/aws-iam";
import { Runtime } from "aws-cdk-lib/aws-lambda";
import { Topic } from "aws-cdk-lib/aws-sns";
import { EmailSubscription } from "aws-cdk-lib/aws-sns-subscriptions";

interface SecurityHQProps extends GuStackProps {
  /**
   * Which application build to run.
   * This will typically match the build number provided by CI.
   */
  buildIdentifier: string;
}

export class SecurityHQ extends GuStack {
  private static app: AppIdentity = {
    app: "security-hq",
  };

  private static readonly auditBucketName = "gu-security-hq-audit";

  constructor(scope: App, id: string, props: SecurityHQProps) {
    super(scope, id, props);

    const { buildIdentifier } = props;

    // Used by outdated credentials lambda.
    // See IAM_DYNAMO_TABLE_NAME in config file
    const table = new GuDynamoTable(this, "DynamoTable", {
      tableName: `security-hq-iam`,
      removalPolicy: RemovalPolicy.RETAIN,
      readCapacity: 5,
      writeCapacity: 5,
      partitionKey: {
        name: "id",
        type: AttributeType.STRING,
      },
      sortKey: {
        name: "dateNotificationSent",
        type: AttributeType.NUMBER,
      },
      devXBackups: { enabled: true },
    });

    this.overrideLogicalId(table, {
      logicalId: "SecurityHqIamDynamoTable",
      reason: "Migrated from a YAML template",
    });

    const distBucket = GuDistributionBucketParameter.getInstance(this);

    const auditDataS3BucketName = new GuStringParameter(
      this,
      "AuditDataS3BucketName",
      {
        description:
          "Name of the S3 bucket to fetch auditable data from (e.g. Janus data)",
        default: `/${this.stack}/${SecurityHQ.app.app}/audit-data-s3-bucket/name`,
        fromSSM: true,
      },
    );
    const auditDataS3BucketPath = `${this.stack}/${this.stage}/*`;

    const guPutCloudwatchMetricsPolicy = new GuPutCloudwatchMetricsPolicy(this);
    const guGetS3AuditObjectsPolicy = new GuGetS3ObjectsPolicy(
      this,
      "S3AuditRead",
      {
        bucketName: auditDataS3BucketName.valueAsString,
        paths: [auditDataS3BucketPath],
      },
    );
    const configS3BucketPath = `${this.stack}/${this.stage}/${SecurityHQ.app.app}/security-hq.conf`;
    const guGetS3ConfigObjectsPolicy = new GuPolicy(this, "S3ConfigRead", {
      statements: [
        this.loadAccountConfigPolicy(
          distBucket.valueAsString,
          configS3BucketPath,
        ),
      ],
    });
    const guDynamoDBReadPolicy = new GuDynamoDBReadPolicy(this, "DynamoRead", {
      tableName: table.tableName,
    });
    const guDynamoDBWritePolicy = new GuDynamoDBWritePolicy(
      this,
      "DynamoWrite",
      {
        tableName: table.tableName,
      },
    );
    // Allow security HQ to assume roles in watched accounts.
    const guAssumeRolePolicy = new GuAllowPolicy(this, "AssumeRole", {
      resources: ["*"],
      actions: ["sts:AssumeRole"],
    });
    // Get the list of regions.
    const guDescribeRegionsPolicy = new GuAllowPolicy(this, "DescribeRegions", {
      resources: ["*"],
      actions: ["ec2:DescribeRegions"],
    });

    const notificationTopic = new Topic(this, "NotificationTopic", {
      displayName: "Security HQ notifications",
    });
    const emailDest = new GuParameter(this, "CloudwatchAlarmEmailDestination", {
      description: "Send Security HQ cloudwatch alarms to this email address",
    });
    notificationTopic.addSubscription(
      new EmailSubscription(emailDest.valueAsString),
    );

    new GuAlarm(this, "RemovePasswordFailureAlarm", {
      app: SecurityHQ.app.app,
      alarmName:
        "Security HQ failed to remove a vulnerable password (new stack)",
      alarmDescription:
        "The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. " +
        "Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.",
      snsTopicName: notificationTopic.topicName,
      threshold: 1,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: "IamRemovePassword",
        namespace: "SecurityHQ",
        period: Duration.seconds(60),
        statistic: "sum",
        dimensionsMap: {
          ReaperExecutionStatus: "Failure",
        },
      }),
      treatMissingData: TreatMissingData.NOT_BREACHING,
      comparisonOperator: ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
    });

    new GuAlarm(this, "RemovePasswordNotRunningAlarm", {
      app: SecurityHQ.app.app,
      alarmName: "Security HQ failed to emit vulnerable password metrics",
      alarmDescription:
        "The credentials reaper feature of Security HQ emits metrics showing how many actions it has taken, and this alarm lets us know when it does not emit. " +
        "Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.",
      snsTopicName: notificationTopic.topicName,
      threshold: 0,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: "IamRemovePassword",
        namespace: "SecurityHQ",
        period: Duration.days(3), // we do not run sat/sun
        statistic: "sum",
        dimensionsMap: {
          ReaperExecutionStatus: "Success",
        },
      }),
      treatMissingData: TreatMissingData.BREACHING,
      comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
    });

    new GuAlarm(this, "DisableAccessKeyFailureAlarm", {
      app: SecurityHQ.app.app,
      alarmName:
        "Security HQ failed to disable a vulnerable access key (new stack)",
      alarmDescription:
        "The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. " +
        "Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.",
      snsTopicName: notificationTopic.topicName,
      threshold: 1,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: "IamDisableAccessKey",
        namespace: "SecurityHQ",
        period: Duration.seconds(60),
        statistic: "sum",
        dimensionsMap: {
          ReaperExecutionStatus: "Failure",
        },
      }),
      treatMissingData: TreatMissingData.NOT_BREACHING,
      comparisonOperator: ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
    });

    new GuAlarm(this, "DisableAccessKeyNotRunningAlarm", {
      app: SecurityHQ.app.app,
      alarmName: "Security HQ failed to emit vulnerable access key metrics",
      alarmDescription:
        "The credentials reaper feature of Security HQ emits metrics showing how many actions it has taken, and this alarm lets us know when it does not emit." +
        " Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.",
      snsTopicName: notificationTopic.topicName,
      threshold: 0,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: "IamDisableAccessKey",
        namespace: "SecurityHQ",
        period: Duration.days(3), // we do not run sat/sun
        statistic: "sum",
        dimensionsMap: {
          ReaperExecutionStatus: "Success",
        },
      }),
      treatMissingData: TreatMissingData.BREACHING,
      comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
    });

    new GuAlarm(this, "DisableOutdatedKeysFailureAlarm", {
      app: SecurityHQ.app.app,
      alarmName:
        "Security HQ failed to disable an outdated access key (new stack)",
      alarmDescription:
        "The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. " +
        "Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.",
      snsTopicName: notificationTopic.topicName,
      threshold: 1,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: "IamDisableOutdatedKeys",
        namespace: "SecurityHQ",
        period: Duration.seconds(60),
        statistic: "sum",
        dimensionsMap: {
          ReaperExecutionStatus: "Failure",
        },
      }),
      treatMissingData: TreatMissingData.NOT_BREACHING,
      comparisonOperator: ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
    });

    new GuAlarm(this, "DisableOutdatedKeysNotRunningAlarm", {
      app: SecurityHQ.app.app,
      alarmName: "Security HQ failed to emit outdated access key metrics",
      alarmDescription:
        "The credentials reaper feature of Security HQ emits metrics showing how many actions it has taken, and this alarm lets us know when it does not emit." +
        " Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.",
      snsTopicName: notificationTopic.topicName,
      threshold: 0,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: "IamDisableOutdatedKeys",
        namespace: "SecurityHQ",
        period: Duration.days(3), // we do not run sat/sun
        statistic: "sum",
        dimensionsMap: {
          ReaperExecutionStatus: "Success",
        },
      }),
      treatMissingData: TreatMissingData.BREACHING,
      comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
    });

    new GuDeveloperPolicyExperimental(this, "RunSecurityHqLocallyPolicy", {
      grantId: "security-hq-dev",
      friendlyName: "Run Security HQ lambdas locally",
      withoutPolicyChecks: true,
      statements: [
        this.getCallerIdentityPolicy(),
        this.getArtifactBucketParameterPolicy(),
        this.getLocalDevConfigS3Policy(distBucket.valueAsString),
        this.loadAccountConfigPolicy(
          distBucket.valueAsString,
          configS3BucketPath,
        ),
        this.listAuditBucketPolicy(),
        this.getAuditObjectPolicy(),
        this.discoverRegionsPolicy(),
        this.getIamCredentialReportPolicy(),
        this.getCloudformationStacksPolicy(),
        this.getUserInfoPolicy(),
      ],
    });

    const iamOutdatedCredentialsLambdaAdditionalPolicies = [
      GuAnghammaradSenderPolicy.getInstance(this),
      guPutCloudwatchMetricsPolicy,
      guGetS3AuditObjectsPolicy,
      guGetS3ConfigObjectsPolicy,
      guDynamoDBReadPolicy,
      guDynamoDBWritePolicy,
      guAssumeRolePolicy,
      guDescribeRegionsPolicy,
    ];

    const iamOutdatedCredentialsLambda = new GuScheduledLambda(
      this,
      "iam-outdated-credentials",
      {
        monitoringConfiguration: {
          // Tolerates 0 failures (triggers an alarm if any execution fails)
          toleratedErrorPercentage: 0,
          snsTopicName:
            GuAnghammaradTopicParameter.getInstance(this).valueAsString,
        },
        rules: [
          {
            schedule: Schedule.cron({
              minute: "0",
              hour: "9,14",
              weekDay: "MON-FRI",
            }),
            description:
              "Run iam-outdated-credentials lambda, Monday-Friday at 9AM and 2PM",
          },
        ],
        app: "iam-outdated-credentials",
        runtime: Runtime.JAVA_21,
        handler: "logic.IamOutdatedCredentialsLambda::handleRequest",
        timeout: Duration.minutes(10),
        environment: {
          STACK: this.stack,
          STAGE: this.stage,
          DRY_RUN: "false",
          CONFIG_BUCKET: "security-dist",
          CONFIG_KEY: `security/${this.stage}/security-hq/security-hq.conf`,
        },
        fileName: `iam-outdated-credentials-${buildIdentifier}.jar`,
      },
    );
    iamOutdatedCredentialsLambdaAdditionalPolicies.forEach((policy) => {
      iamOutdatedCredentialsLambda.role!.attachInlinePolicy(policy);
    });

    const iamUnrecognisedUsersLambdaAdditionalPolicies = [
      GuAnghammaradSenderPolicy.getInstance(this),
      guPutCloudwatchMetricsPolicy,
      guGetS3AuditObjectsPolicy,
      guGetS3ConfigObjectsPolicy,
      guAssumeRolePolicy,
      guDescribeRegionsPolicy,
    ];

    const iamUnrecognisedUsersLambda = new GuScheduledLambda(
      this,
      "iam-unrecognised-users",
      {
        monitoringConfiguration: {
          // Tolerates 0 failures (triggers an alarm if any execution fails)
          toleratedErrorPercentage: 0,
          snsTopicName:
            GuAnghammaradTopicParameter.getInstance(this).valueAsString,
        },
        rules: [
          {
            schedule: Schedule.cron({
              minute: "0",
              hour: "9,14",
              weekDay: "MON-FRI",
            }),
            description:
              "Run iam-unrecognised-users lambda, Monday-Friday at 9AM and 2PM",
          },
        ],
        app: "iam-unrecognised-users",
        runtime: Runtime.JAVA_21,
        handler: "unrecognised.Handler::handleRequest",
        timeout: Duration.minutes(10),
        environment: {
          STACK: this.stack,
          STAGE: this.stage,
          DRY_RUN: "false",
          CONFIG_BUCKET: "security-dist",
          CONFIG_KEY: `security/${this.stage}/security-hq/security-hq.conf`,
          IAM_UNRECOGNISED_USER_S3_BUCKET: SecurityHQ.auditBucketName,
          IAM_UNRECOGNISED_USER_S3_KEY: `security/${this.stage}/janus-data-export/janusData.conf`,
        },
        fileName: `iam-unrecognised-users-${buildIdentifier}.jar`,
      },
    );
    iamUnrecognisedUsersLambdaAdditionalPolicies.forEach((policy) => {
      iamUnrecognisedUsersLambda.role!.attachInlinePolicy(policy);
    });
  }

  private getCallerIdentityPolicy() {
    // Used by setup to check that valid, non-expired credentials are configured
    return new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["sts:GetCallerIdentity"],
      resources: ["*"],
    });
  }

  private getIamCredentialReportPolicy() {
    // ONLY used when running lambda locally because it would usually be obtained from an assumed role
    return new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["iam:GenerateCredentialReport", "iam:GetCredentialReport"],
      resources: ["*"],
    });
  }

  private getCloudformationStacksPolicy() {
    // ONLY used when running lambda locally because it would usually be obtained from an assumed role
    return new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["cloudformation:DescribeStacks", "cloudformation:ListStacks"],
      resources: ["*"],
    });
  }

  private getUserInfoPolicy() {
    // ONLY used when running lambda locally because it would usually be obtained from an assumed role
    return new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["iam:ListUserTags", "iam:ListAccessKeys", "iam:ListMFADevices"],
      resources: ["*"],
    });
  }

  private getArtifactBucketParameterPolicy() {
    // Used by setup to look up distribution bucket name
    return new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["ssm:GetParameter"],
      resources: [
        `arn:aws:ssm:${this.region}:${this.account}:parameter/account/services/artifact.bucket`,
      ],
    });
  }

  private getLocalDevConfigS3Policy(bucketName: string) {
    // Used by setup to download local dev configuration and service account cert
    return new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["s3:GetObject"],
      resources: [`arn:aws:s3:::${bucketName}/security/DEV/security-hq/*`],
    });
  }

  private loadAccountConfigPolicy(bucketName: string, path: string) {
    // Used by lambdas to load the security-hq.conf file
    return new PolicyStatement({
      sid: "LoadAccountConfig",
      effect: Effect.ALLOW,
      actions: ["s3:GetObject"],
      resources: [`arn:aws:s3:::${bucketName}/${path}`],
    });
  }

  private listAuditBucketPolicy() {
    // Used by lambdas
    return new PolicyStatement({
      sid: "ListAuditBucket",
      effect: Effect.ALLOW,
      actions: ["s3:ListBucket"],
      resources: [`arn:aws:s3:::${SecurityHQ.auditBucketName}`],
    });
  }

  private getAuditObjectPolicy() {
    // Used by lambdas
    return new PolicyStatement({
      sid: "GetAuditObject",
      effect: Effect.ALLOW,
      actions: ["s3:GetObject"],
      resources: [`arn:aws:s3:::${SecurityHQ.auditBucketName}/*`],
    });
  }

  private discoverRegionsPolicy() {
    // Used by lambdas
    return new PolicyStatement({
      sid: "DiscoverRegions",
      effect: Effect.ALLOW,
      actions: ["ec2:DescribeRegions"],
      resources: ["*"],
    });
  }
}
