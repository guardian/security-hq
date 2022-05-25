import 'source-map-support/register';
import { App } from 'aws-cdk-lib';
import { SecurityVpc } from '../lib/security-vpc';

// Note, this stack is synthed locally rather than in CI as it is
// 'environment-aware'
// (https://docs.aws.amazon.com/cdk/latest/guide/environments.html) and so
// requires AWS crendentials. Ideally, longer-term we'd support this in Github
// Actions somehow.
const app = new App();
new SecurityVpc(app, 'security-vpc', {
  stack: 'security',
  stage: 'PROD',
  env: { region: 'eu-west-1', account: process.env.CDK_DEFAULT_ACCOUNT }, // Crucial to ensure VPC uses all AZs.
});
