import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuVpc } from '@guardian/cdk/lib/constructs/vpc';
import type { App } from 'aws-cdk-lib';

export class SecurityVpc extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);
    new GuVpc(this, 'SecurityVpc', { cidr: '10.248.208.0/21' });
  }
}
