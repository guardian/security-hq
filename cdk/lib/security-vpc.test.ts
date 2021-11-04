import '@aws-cdk/assert/jest';
import { SynthUtils } from '@aws-cdk/assert';
import { App } from '@aws-cdk/core';
import { SecurityVpc } from './security-vpc';

describe('VPC stack', () => {
  it('matches the snapshot', () => {
    const app = new App();
    const stack = new SecurityVpc(app, 'security-vpc', { stack: 'security' });
    expect(SynthUtils.toCloudFormation(stack)).toMatchSnapshot();
  });
});
