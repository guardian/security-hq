import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { SecurityVpc } from './security-vpc';

describe('VPC stack', () => {
  it('matches the snapshot', () => {
    const app = new App();
    const stack = new SecurityVpc(app, 'security-vpc', {
      stack: 'security',
      stage: 'PROD',
    });
    expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
  });
});
