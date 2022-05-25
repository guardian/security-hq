import 'source-map-support/register';
import { App } from 'aws-cdk-lib';
import { SecurityHQ } from '../lib/security-hq';

const app = new App();
new SecurityHQ(app, 'security-hq', { stack: 'security', stage: 'PROD' });
