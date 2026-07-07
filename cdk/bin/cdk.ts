import "source-map-support/register";
import { GuRoot } from '@guardian/cdk/lib/constructs/root';
import { SecurityHQ } from "../lib/security-hq";

const app = new GuRoot();

new SecurityHQ(app, "security-hq", {
  stack: "security",
  stage: "PROD",
  cloudFormationStackName: "security-hq-PROD",
  env: { region: "eu-west-1" },
  buildIdentifier: process.env["BUILD_NUMBER"] ?? "DEV",
});
