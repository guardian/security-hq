import { CfnInclude } from '@aws-cdk/cloudformation-include';
import type { App } from '@aws-cdk/core';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack, GuStageParameter } from '@guardian/cdk/lib/constructs/core';

export class SecurityHQ extends GuStack {
  migratedFromCloudFormation = true;

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    new CfnInclude(this, `security-hq-PROD`, {
      templateFile: '../cloudformation/security-hq.template.yaml',
      parameters: { Stage: GuStageParameter.getInstance(this) },
    });
  }
}
