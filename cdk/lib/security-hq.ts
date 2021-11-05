import {
  ComparisonOperator,
  Metric,
  TreatMissingData,
} from '@aws-cdk/aws-cloudwatch';
import { AttributeType, Table } from '@aws-cdk/aws-dynamodb';
import { InstanceClass, InstanceSize, InstanceType } from '@aws-cdk/aws-ec2';
import { Duration, RemovalPolicy } from '@aws-cdk/core';
import type { App, CfnElement } from '@aws-cdk/core';
import { AccessScope, GuApplicationPorts, GuEc2App } from '@guardian/cdk';
import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
  GuDistributionBucketParameter,
  GuParameter,
  GuStack,
} from '@guardian/cdk/lib/constructs/core';
import {
  GuDynamoDBReadPolicy,
  GuDynamoDBWritePolicy,
} from '@guardian/cdk/lib/constructs/iam';

export class SecurityHQ extends GuStack {
  migratedFromCloudFormation = true;

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    const table = new Table(this, 'DynamoTable', {
      tableName: `security-hq-iam-${this.stage}`,
      removalPolicy: RemovalPolicy.RETAIN,
      readCapacity: 5,
      writeCapacity: 5,
      partitionKey: {
        name: 'id',
        type: AttributeType.STRING,
      },
    });

    // Required as migrated from existing Cloudformation stack.
    const defaultChild = table.node.defaultChild as unknown as CfnElement;
    defaultChild.overrideLogicalId('SecurityHqIamDynamoTable');

    const distBucket = GuDistributionBucketParameter.getInstance(this);

    // Create new ALB-based app, that will live in the new VPC. This will run
    // simultaneously with the old service. Once confident, DNS can be switched
    // and the old app deleted.
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
            tableName: table.tableName,
          }),
          new GuDynamoDBWritePolicy(this, 'DynamoWrite', {
            tableName: table.tableName,
          }),
        ],
      },
    });

    // TODO add this to GuCDK standard SSM parameters.
    const alarmTopic = new GuParameter(this, 'TopicName', {
      type: 'string',
      description: "ARN for Anghammarad's SNS topic",
    });

    new GuAlarm(this, 'RemovePasswordExecutionFailureAlarm', {
      alarmName: 'Security HQ failed to remove a vulnerable password',
      alarmDescription:
        'The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.',
      snsTopicName: alarmTopic.value.toString(),
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
      snsTopicName: alarmTopic.value.toString(),
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
