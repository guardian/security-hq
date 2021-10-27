import type { CfnTable } from '@aws-cdk/aws-dynamodb';
import { Table } from '@aws-cdk/aws-dynamodb';
import { InstanceClass, InstanceSize, InstanceType } from '@aws-cdk/aws-ec2';
import { CfnInclude } from '@aws-cdk/cloudformation-include';
import type { App } from '@aws-cdk/core';
import { AccessScope, GuApplicationPorts, GuEc2App } from '@guardian/cdk';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
  GuPrivateConfigBucketParameter,
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

    const template = new CfnInclude(this, `security-hq-PROD`, {
      templateFile: '../cloudformation/security-hq.template.yaml',
      parameters: [], // TODO
    });

    // Import the existing template, though note we have removed some
    // overlapping resources - such as the stage parameter.
    const cfnTable = template.getResource(
      'SecurityHqIamDynamoTable'
    ) as CfnTable;

    // Import table to use for permissions later.
    const table = Table.fromTableName(this, 'DynamoTable', cfnTable.ref);

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
      userData: {
        distributable: {
          fileName: 'security-hq.deb',
          executionStatement: 'dpkg -i security-hq.deb',
        },
        configuration: {
          bucket: new GuPrivateConfigBucketParameter(this),
          files: ['security-hq-service-account-cert.json', 'security-hq.conf'],
        },
      },
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
  }
}
