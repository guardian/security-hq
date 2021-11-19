import {
  ComparisonOperator,
  Metric,
  TreatMissingData,
} from '@aws-cdk/aws-cloudwatch';
import type { CfnTable } from '@aws-cdk/aws-dynamodb';
import {
  InstanceClass,
  InstanceSize,
  InstanceType,
  Peer,
} from '@aws-cdk/aws-ec2';
import type { CfnTopic } from '@aws-cdk/aws-sns';
import { CfnInclude } from '@aws-cdk/cloudformation-include';
import { Duration, Tags } from '@aws-cdk/core';
import type { App } from '@aws-cdk/core';
import { AccessScope, GuApplicationPorts, GuEc2App } from '@guardian/cdk';
import { Stage } from '@guardian/cdk/lib/constants/stage';
import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
  GuDistributionBucketParameter,
  GuStack,
} from '@guardian/cdk/lib/constructs/core';
import type { AppIdentity } from '@guardian/cdk/lib/constructs/core/identity';
import { GuCname } from '@guardian/cdk/lib/constructs/dns';
import {
  GuAllowPolicy,
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
  private static app: AppIdentity = {
    app: 'security-hq',
  };

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

    // Import the existing Dynamo table.
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

    // TODO replace with config repo once token access for GHA is sorted
    // (https://trello.com/c/zhdgXpmk/903-allow-github-actions-to-clone-private-infrastructure-config).
    const accessRestrictionCidr = template.getParameter(
      'AccessRestrictionCidr'
    ).valueAsString;

    const domainNames = {
      [Stage.CODE]: { domainName: 'security-hq.code.dev-gutools.co.uk' },
      [Stage.PROD]: { domainName: 'security-hq.gutools.co.uk' },
    };

    const ec2App = new GuEc2App(this, {
      access: {
        scope: AccessScope.RESTRICTED,
        cidrRanges: [Peer.ipv4(accessRestrictionCidr)],
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
          new GuDynamoDBReadPolicy(this, 'DynamoRead', {
            tableName: cfnTable.tableName as string,
          }),
          new GuDynamoDBWritePolicy(this, 'DynamoWrite', {
            tableName: cfnTable.tableName as string,
          }),
          new GuAllowPolicy(this, 'SsmCommands', {
            resources: ['*'],
            actions: [
              'ec2messages:AcknowledgeMessage',
              'ec2messages:DeleteMessage',
              'ec2messages:FailMessage',
              'ec2messages:GetEndpoint',
              'ec2messages:GetMessages',
              'ec2messages:SendReply',
              'ssm:UpdateInstanceInformation',
              'ssm:ListInstanceAssociations',
              'ssm:DescribeInstanceProperties',
              'ssm:DescribeDocumentParameters',
              'ssmmessages:CreateControlChannel',
              'ssmmessages:CreateDataChannel',
              'ssmmessages:OpenControlChannel',
              'ssmmessages:OpenDataChannel',
            ],
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

    // Required to support 2 ASGs at the same time in a RR deploy.
    Tags.of(ec2App.autoScalingGroup).add('gu:riffraff:new-asg', 'true');

    new GuCname(this, 'security-hq.gutools.co.uk', {
      app: SecurityHQ.app.app,
      domainNameProps: domainNames,
      ttl: Duration.minutes(1), // Temporarily low during DNS migration.
      resourceRecord: ec2App.loadBalancer.loadBalancerDnsName,
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

    new GuAlarm(this, 'RemovePasswordFailureAlarm', {
      app: SecurityHQ.app.app,
      alarmName:
        'Security HQ failed to remove a vulnerable password (new stack)',
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

    new GuAlarm(this, 'DisableAccessKeyFailureAlarm', {
      app: SecurityHQ.app.app,
      alarmName:
        'Security HQ failed to disable a vulnerable access key (new stack)',
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
