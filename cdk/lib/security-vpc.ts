import type { App } from '@aws-cdk/core';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuKinesisStream } from '@guardian/cdk/lib/constructs/kinesis';

export class SecurityVpc extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    new GuKinesisStream(this, 'Never gonna happen', {});
    // new GuVpc(this, 'SecurityVpc', {}); // TODO specify CIDR
  }
}
