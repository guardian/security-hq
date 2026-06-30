import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import {
    GuDistributionBucketParameter,
    GuStack,
} from "@guardian/cdk/lib/constructs/core";
import type { AppIdentity } from "@guardian/cdk/lib/constructs/core/identity";
import { GuScheduledLambda } from "@guardian/cdk/lib/patterns/scheduled-lambda";
import type { App } from "aws-cdk-lib";
import { Duration } from "aws-cdk-lib";
import { Schedule } from "aws-cdk-lib/aws-events";
import { Effect, PolicyStatement } from "aws-cdk-lib/aws-iam";
import { Runtime } from "aws-cdk-lib/aws-lambda";

export type CloudwatchMetricsProps = GuStackProps;

/**
 * Scheduled Lambda that fetches Security HQ vulnerability data directly from
 * Trusted Advisor and IAM and publishes it as CloudWatch metrics.
 *
 * It mirrors the in-app `MetricService`, running every 6 hours. It starts in
 * dry-run mode (`DRY_RUN=true`) so that it logs the metrics it would publish
 * without writing them, allowing the output to be verified before it takes over
 * from the in-app metrics.
 */
export class CloudwatchMetrics extends GuStack {
    private static app: AppIdentity = {
        app: "cloudwatch-metrics",
    };

    constructor(scope: App, id: string, props: CloudwatchMetricsProps) {
        super(scope, id, props);

        const distBucket = GuDistributionBucketParameter.getInstance(this);
        const configKey = `security/${this.stage}/security-hq/security-hq.conf`;

        const lambda = new GuScheduledLambda(this, "cloudwatch-metrics", {
            app: CloudwatchMetrics.app.app,
            runtime: Runtime.JAVA_25,
            handler: "metrics.Handler::handleRequest",
            fileName: "cloudwatch-metrics.jar",
            memorySize: 1024,
            timeout: Duration.minutes(5),
            environment: {
                DRY_RUN: "true",
                CONFIG_BUCKET: distBucket.valueAsString,
                CONFIG_KEY: configKey,
                REGION: this.region,
            },
            rules: [
                {
                    schedule: Schedule.rate(Duration.hours(6)),
                    description: "Publish Security HQ vulnerability metrics to CloudWatch",
                },
            ],
            monitoringConfiguration: { noMonitoring: true },
        });

        const policyStatements = [
            // Publish the vulnerability metrics (equivalent to GuPutCloudwatchMetricsPolicy).
            new PolicyStatement({
                effect: Effect.ALLOW,
                actions: ["cloudwatch:PutMetricData"],
                resources: ["*"],
            }),
            // Read the Security HQ config (account list) from the distribution bucket.
            new PolicyStatement({
                effect: Effect.ALLOW,
                actions: ["s3:GetObject"],
                resources: [`arn:aws:s3:::${distBucket.valueAsString}/${configKey}`],
            }),
            // Assume roles in watched accounts to read Trusted Advisor / IAM data.
            new PolicyStatement({
                effect: Effect.ALLOW,
                actions: ["sts:AssumeRole"],
                resources: ["*"],
            }),
            // Get the list of regions.
            new PolicyStatement({
                effect: Effect.ALLOW,
                actions: ["ec2:DescribeRegions"],
                resources: ["*"],
            }),
        ];
        policyStatements.forEach((statement) => lambda.addToRolePolicy(statement));
    }
}
