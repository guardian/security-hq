import '@aws-cdk/assert/jest';
import { SynthUtils } from '@aws-cdk/assert';
import { App } from '@aws-cdk/core';
import { SecurityHQ } from './security-hq';

describe('HQ stack', () => {
  it('matches the snapshot', () => {
    const app = new App();
    const stack = new SecurityHQ(app, 'security-hq', { stack: 'security' });
    expect(SynthUtils.toCloudFormation(stack)).toMatchSnapshot();
  });
});
