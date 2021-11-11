import {
  ComparisonOperator,
  Metric,
  TreatMissingData,
} from '@aws-cdk/aws-cloudwatch';
import type { CfnTable } from '@aws-cdk/aws-dynamodb';
import { InstanceClass, InstanceSize, InstanceType } from '@aws-cdk/aws-ec2';
import type { CfnTopic } from '@aws-cdk/aws-sns';
import { CfnInclude } from '@aws-cdk/cloudformation-include';
import { Duration } from '@aws-cdk/core';
import type { App } from '@aws-cdk/core';
import { AccessScope, GuApplicationPorts, GuEc2App } from '@guardian/cdk';
import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
  GuDistributionBucketParameter,
  GuStack,
} from '@guardian/cdk/lib/constructs/core';
import {
  GuDynamoDBReadPolicy,
  GuDynamoDBWritePolicy,
} from '@guardian/cdk/lib/constructs/iam';

/**
 * Migration steps:
 *
 * - ensure new stack is working okay
 * - make DNS switch (defined in domains account)
 * - include full definitions for adopted resources (dynamo, alarm topic) + stop
 *   importing old template
 * - finally delete old template + stack
 */
export class SecurityHQ extends GuStack {
  migratedFromCloudFormation = true;

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    // Import old stuff. Note, this template has been modified slightly to avoid
    // a clash on the VpcId parameter - as the new service runs in a different
    // VPC to the old.
    const template = new CfnInclude(this, `security-hq-PROD`, {
      templateFile: '../cloudformation/security-hq.template.yaml',
      parameters: {
        Stage: this.stage,
      },
    });

    // Import the existing template, though note we have removed some
    // overlapping resources - such as the stage parameter.
    const cfnTable = template.getResource(
      'SecurityHqIamDynamoTable'
    ) as CfnTable;

    // TODO use below once old template is gone instead of importing above.
    /*     const table = new Table(this, 'DynamoTable', {
      tableName: `security-hq-iam-${this.stage}`,
      removalPolicy: RemovalPolicy.RETAIN, // TODO set update+delete policy too.
      readCapacity: 5,
      writeCapacity: 5,
      partitionKey: {
        name: 'id',
        type: AttributeType.STRING,
      },
    });
    const defaultChild = table.node.defaultChild as unknown as CfnElement;
    defaultChild.overrideLogicalId('SecurityHqIamDynamoTable'); */

    // The new stack below...

    const distBucket = GuDistributionBucketParameter.getInstance(this);

    new GuEc2App(this, {
      access: { scope: AccessScope.PUBLIC },
      app: 'security-hq',
      applicationPort: GuApplicationPorts.Play,
      instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.LARGE),
      certificateProps: {
        PROD: { domainName: 'security-hq.gutools.co.uk' },
        CODE: { domainName: 'security-hq.code.dev-gutools.co.uk' }, // Note, Security HQ does not in fact have a CODE stage.
      },
      monitoringConfiguration: { noMonitoring: true },
      scaling: {
        CODE: { minimumInstances: 1 },
        PROD: { minimumInstances: 1 },
      },
      userData: `
#!/bin/bash -ev
# setup security-hq
mkdir -p /etc/gu

aws --region eu-west-1 s3 cp s3://${distBucket.value.toString()}/security/${
        this.stage
      }/security-hq/security-hq.conf /etc/gu
aws --region eu-west-1 s3 cp s3://${distBucket.value.toString()}/security/${
        this.stage
      }/security-hq/security-hq-service-account-cert.json /etc/gu
aws --region eu-west-1 s3 cp s3://${distBucket.value.toString()}/security/${
        this.stage
      }/security-hq/security-hq.deb /tmp/installer.deb

dpkg -i /tmp/installer.deb`,
      roleConfiguration: {
        additionalPolicies: [
          new GuDynamoDBReadPolicy(this, 'DynamoRead', {
            tableName: cfnTable.tableName as string,
          }),
          new GuDynamoDBWritePolicy(this, 'DynamoWrite', {
            tableName: cfnTable.tableName as string,
          }),
        ],
      },
    });

    // TODO replace once template deleted with commented code below.
    const notificationTopic = template.getResource(
      'NotificationTopic'
    ) as CfnTopic;

    /*     const notificationTopic = new GuSnsTopic(this, 'NotificationTopic', {
      displayName: 'Security HQ notifications',
    });
    const emailDest = new GuParameter(this, 'CloudwatchAlarmEmailDestination', {
      description: 'Send Security HQ cloudwatch alarms to this email address',
    });
    notificationTopic.addSubscription(
      new EmailSubscription(emailDest.valueAsString)
    ); */

    new GuAlarm(this, 'RemovePasswordExecutionFailureAlarm', {
      alarmName: 'Security HQ failed to remove a vulnerable password',
      alarmDescription:
        'The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.',
      snsTopicName: notificationTopic.topicName as string,
      threshold: 1,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: 'IamRemovePassword',
        namespace: 'SecurityHQ',
        period: Duration.seconds(60),
        statistic: 'sum',
        dimensionsMap: {
          ReaperExecutionStatus: 'Failure',
        },
      }),
      treatMissingData: TreatMissingData.NOT_BREACHING,
      comparisonOperator: ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
    });

    new GuAlarm(this, 'DisableAccessKeyExecutionFailureAlarm', {
      alarmName: 'Security HQ failed to disable a vulnerable access key',
      alarmDescription:
        'The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.',
      snsTopicName: notificationTopic.topicName as string,
      threshold: 1,
      evaluationPeriods: 1,
      metric: new Metric({
        metricName: 'IamDisableAccessKey',
        namespace: 'SecurityHQ',
        period: Duration.seconds(60),
        statistic: 'sum',
        dimensionsMap: {
          ReaperExecutionStatus: 'Failure',
        },
      }),
      treatMissingData: TreatMissingData.NOT_BREACHING,
      comparisonOperator: ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
    });
  }
}
