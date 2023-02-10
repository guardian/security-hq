import { GuEc2App } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
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
import { GuHttpsEgressSecurityGroup } from '@guardian/cdk/lib/constructs/ec2';
import {
  GuAllowPolicy,
  GuDynamoDBReadPolicy,
  GuDynamoDBWritePolicy,
  GuGetS3ObjectsPolicy,
  GuPutCloudwatchMetricsPolicy,
} from '@guardian/cdk/lib/constructs/iam';
import { GuAnghammaradSenderPolicy } from '@guardian/cdk/lib/constructs/iam/policies/anghammarad';
import type { GuApplicationLoadBalancer } from '@guardian/cdk/lib/constructs/loadbalancing';
import type { App } from 'aws-cdk-lib';
import { Duration, RemovalPolicy, SecretValue } from 'aws-cdk-lib';
import { CfnCertificate } from 'aws-cdk-lib/aws-certificatemanager';
import {
  ComparisonOperator,
  MathExpression,
  Metric,
  TreatMissingData,
} from 'aws-cdk-lib/aws-cloudwatch';
import { AttributeType, Table } from 'aws-cdk-lib/aws-dynamodb';
import { InstanceClass, InstanceSize, InstanceType } from 'aws-cdk-lib/aws-ec2';
import {
  HttpCodeElb,
  ListenerAction,
  ListenerCondition,
  UnauthenticatedAction,
} from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Topic } from 'aws-cdk-lib/aws-sns';
import { EmailSubscription } from 'aws-cdk-lib/aws-sns-subscriptions';
import {
  ParameterDataType,
  ParameterTier,
  StringParameter,
} from 'aws-cdk-lib/aws-ssm';

export class SecurityHQ extends GuStack {
  private static app: AppIdentity = {
    app: 'security-hq',
  };

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    function httpErrorRateAlarm(
      scope: GuStack,
      alb: GuApplicationLoadBalancer,
      responseType: HttpCodeElb,
      notificationTopic: Topic,
      period: Duration = Duration.minutes(10),
      errorPercentage: number = 10,
      evaluationPeriods: number = 1
    ): GuAlarm {
      const errorMetric = alb.metricHttpCodeElb(responseType);

      const mathExpression = new MathExpression({
        expression: '(m1/m2)*100',
        period,
        usingMetrics: {
          m1: errorMetric,
          m2: alb.metricRequestCount(),
        },
      });

      return new GuAlarm(scope, `${errorMetric.metricName}-percentage`, {
        app: SecurityHQ.app.app,
        metric: mathExpression,
        alarmDescription: `${errorMetric.metricName} errors as a percentage of all requests.`,
        snsTopicName: notificationTopic.topicName,
        threshold: errorPercentage,
        evaluationPeriods,
        alarmName: `${errorMetric.metricName}-percentage`,
        comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: TreatMissingData.MISSING,
      });
    }

    const table = new Table(this, 'DynamoTable', {
      tableName: `security-hq-iam`,
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

    this.overrideLogicalId(table, {
      logicalId: 'SecurityHqIamDynamoTable',
      reason: 'Migrated from a YAML template',
    });

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

    const domainName = 'public.security-hq.gutools.co.uk';

    const ec2App = new GuEc2App(this, {
      access: {
        scope: AccessScope.PUBLIC,
      },
      app: 'security-hq',
      applicationPort: 9000,
      instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.LARGE),
      certificateProps: {
        domainName,
      },
      monitoringConfiguration: { noMonitoring: true },
      scaling: {
        minimumInstances: 1,
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

    // Despite what the ID value is, this CNAME is for public.security-hq.gutools.co.uk
    new GuCname(this, 'security-hq.gutools.co.uk', {
      app: SecurityHQ.app.app,
      domainName,
      ttl: Duration.hours(1),
      resourceRecord: ec2App.loadBalancer.loadBalancerDnsName,
    });

    const certificate = this.node
      .findAll()
      .find((_) => _ instanceof CfnCertificate) as CfnCertificate;

    certificate.subjectAlternativeNames = [
      ...(certificate.subjectAlternativeNames ?? []),
      'security-hq.gutools.co.uk',
    ];

    new GuCname(this, 'DnsRecord', {
      app: SecurityHQ.app.app,
      domainName: 'security-hq.gutools.co.uk',
      ttl: Duration.hours(1),
      resourceRecord: ec2App.loadBalancer.loadBalancerDnsName,
    });

    // Need to give the ALB outbound access on 443 for the IdP endpoints (to support Google Auth).
    const outboundHttpsSecurityGroup = new GuHttpsEgressSecurityGroup(
      this,
      'idp-access',
      {
        app: SecurityHQ.app.app,
        vpc: ec2App.vpc,
      }
    );

    ec2App.loadBalancer.addSecurityGroup(outboundHttpsSecurityGroup);

    // This parameter is used by https://github.com/guardian/waf
    new StringParameter(this, 'AlbSsmParam', {
      parameterName: `/infosec/waf/services/${this.stage}/security-hq-alb-arn`,
      description: `The arn of the ALB for security-hq-${this.stage}. N.B. this parameter is created via cdk`,
      simpleName: false,
      stringValue: ec2App.loadBalancer.loadBalancerArn,
      tier: ParameterTier.STANDARD,
      dataType: ParameterDataType.TEXT,
    });

    const clientId = new GuStringParameter(this, 'ClientId', {
      description: 'Google OAuth client ID',
    });

    ec2App.listener.addAction('DefaultAction', {
      action: ListenerAction.authenticateOidc({
        authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
        issuer: 'https://accounts.google.com',
        scope: 'openid',
        authenticationRequestExtraParams: { hd: 'guardian.co.uk' },
        onUnauthenticatedRequest: UnauthenticatedAction.AUTHENTICATE,
        tokenEndpoint: 'https://oauth2.googleapis.com/token',
        userInfoEndpoint: 'https://openidconnect.googleapis.com/v1/userinfo',
        clientId: clientId.valueAsString,
        clientSecret: SecretValue.secretsManager(
          `/${this.stage}/deploy/security-hq/client-secret`
        ),
        next: ListenerAction.forward([ec2App.targetGroup]),
      }),
    });

    ec2App.listener.addAction('redirect', {
      action: ListenerAction.redirect({
        permanent: true,
        host: 'security-hq.gutools.co.uk',
      }),
      conditions: [
        ListenerCondition.hostHeaders(['public.security-hq.gutools.co.uk']),
      ],
      priority: 1,
    });

    const notificationTopic = new Topic(this, 'NotificationTopic', {
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

    httpErrorRateAlarm(
      this,
      ec2App.loadBalancer,
      HttpCodeElb.ELB_5XX_COUNT,
      notificationTopic
    );
    httpErrorRateAlarm(
      this,
      ec2App.loadBalancer,
      HttpCodeElb.ELB_4XX_COUNT,
      notificationTopic
    );
  }
}
