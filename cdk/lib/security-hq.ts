import {
  ComparisonOperator,
  Metric,
  TreatMissingData,
} from '@aws-cdk/aws-cloudwatch';
import { AttributeType, Table } from '@aws-cdk/aws-dynamodb';
import {
  InstanceClass,
  InstanceSize,
  InstanceType,
  Peer,
} from '@aws-cdk/aws-ec2';
import { EmailSubscription } from '@aws-cdk/aws-sns-subscriptions';
import type { App, CfnElement } from '@aws-cdk/core';
import { Duration, RemovalPolicy } from '@aws-cdk/core';
import { AccessScope, GuApplicationPorts, GuEc2App } from '@guardian/cdk';
import { Stage } from '@guardian/cdk/lib/constants/stage';
import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
  GuDistributionBucketParameter,
  GuParameter,
  GuStack,
  GuStringParameter,
} from '@guardian/cdk/lib/constructs/core';
import type { AppIdentity } from '@guardian/cdk/lib/constructs/core/identity';
import { GuCname } from '@guardian/cdk/lib/constructs/dns';
import {
  GuAllowPolicy,
  GuDynamoDBReadPolicy,
  GuDynamoDBWritePolicy,
  GuGetS3ObjectsPolicy,
  GuPutCloudwatchMetricsPolicy,
} from '@guardian/cdk/lib/constructs/iam';
import { GuAnghammaradSenderPolicy } from '@guardian/cdk/lib/constructs/iam/policies/anghammarad';
import { GuSnsTopic } from '@guardian/cdk/lib/constructs/sns';
import { GuardianPublicNetworks } from '@guardian/private-infrastructure-config';

export class SecurityHQ extends GuStack {
  private static app: AppIdentity = {
    app: 'security-hq',
  };

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
      sortKey: {
        name: 'dateNotificationSent',
        type: AttributeType.NUMBER,
      },
    });
    const defaultChild = table.node.defaultChild as unknown as CfnElement;
    defaultChild.overrideLogicalId('SecurityHqIamDynamoTable');

    const distBucket = GuDistributionBucketParameter.getInstance(this);

    const auditDataS3BucketName = new GuStringParameter(
      this,
      'AuditDataS3BucketName',
      {
        description:
          'Name of the S3 bucket to fetch auditable data from (e.g. Janus data)',
        default: `/${this.stack}/${SecurityHQ.app.app}/audit-data-s3-bucket/name`,
        fromSSM: true,
      }
    );
    const auditDataS3BucketPath = `${this.stack}/${this.stage}/*`;

    const domainNames = {
      [Stage.CODE]: { domainName: 'security-hq.code.dev-gutools.co.uk' },
      [Stage.PROD]: { domainName: 'security-hq.gutools.co.uk' },
    };

    const ec2App = new GuEc2App(this, {
      access: {
        scope: AccessScope.RESTRICTED,
        cidrRanges: [Peer.ipv4(GuardianPublicNetworks.London)],
      },
      app: 'security-hq',
      applicationPort: GuApplicationPorts.Play,
      instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.LARGE),
      certificateProps: domainNames,
      monitoringConfiguration: { noMonitoring: true },
      scaling: {
        CODE: { minimumInstances: 1 },
        PROD: { minimumInstances: 1 },
      },
      userData: `#!/bin/bash -ev
# setup security-hq
mkdir -p /etc/gu

aws --region eu-west-1 s3 cp s3://${distBucket.valueAsString}/security/${this.stage}/security-hq/security-hq.conf /etc/gu
aws --region eu-west-1 s3 cp s3://${distBucket.valueAsString}/security/${this.stage}/security-hq/security-hq-service-account-cert.json /etc/gu
aws --region eu-west-1 s3 cp s3://${distBucket.valueAsString}/security/${this.stage}/security-hq/security-hq.deb /tmp/installer.deb

dpkg -i /tmp/installer.deb`,
      roleConfiguration: {
        additionalPolicies: [
          GuAnghammaradSenderPolicy.getInstance(this),
          new GuPutCloudwatchMetricsPolicy(this),
          new GuGetS3ObjectsPolicy(this, 'S3AuditRead', {
            bucketName: auditDataS3BucketName.valueAsString,
            paths: [auditDataS3BucketPath],
          }),
          new GuDynamoDBReadPolicy(this, 'DynamoRead', {
            tableName: table.tableName,
          }),
          new GuDynamoDBWritePolicy(this, 'DynamoWrite', {
            tableName: table.tableName,
          }),
          // Allow security HQ to assume roles in watched accounts.
          new GuAllowPolicy(this, 'AssumeRole', {
            resources: ['*'],
            actions: ['sts:AssumeRole'],
          }),
          // Get the list of regions.
          new GuAllowPolicy(this, 'DescribeRegions', {
            resources: ['*'],
            actions: ['ec2:DescribeRegions'],
          }),
        ],
      },
    });

    new GuCname(this, 'security-hq.gutools.co.uk', {
      app: SecurityHQ.app.app,
      domainNameProps: domainNames,
      ttl: Duration.hours(1),
      resourceRecord: ec2App.loadBalancer.loadBalancerDnsName,
    });

    const notificationTopic = new GuSnsTopic(this, 'NotificationTopic', {
      displayName: 'Security HQ notifications',
    });
    const emailDest = new GuParameter(this, 'CloudwatchAlarmEmailDestination', {
      description: 'Send Security HQ cloudwatch alarms to this email address',
    });
    notificationTopic.addSubscription(
      new EmailSubscription(emailDest.valueAsString)
    );

    new GuAlarm(this, 'RemovePasswordFailureAlarm', {
      app: SecurityHQ.app.app,
      alarmName:
        'Security HQ failed to remove a vulnerable password (new stack)',
      alarmDescription:
        'The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.',
      snsTopicName: notificationTopic.topicName,
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

    new GuAlarm(this, 'DisableAccessKeyFailureAlarm', {
      app: SecurityHQ.app.app,
      alarmName:
        'Security HQ failed to disable a vulnerable access key (new stack)',
      alarmDescription:
        'The credentials reaper feature of Security HQ logs either success or failure to cloudwatch, and this alarm lets us know when it logs a failure. Check the application logs for more details https://logs.gutools.co.uk/s/devx/goto/f9915a6e4e94a000732d67026cea91be.',
      snsTopicName: notificationTopic.topicName,
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
