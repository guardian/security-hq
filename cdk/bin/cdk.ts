import "source-map-support/register";
import { App } from "aws-cdk-lib";
import { CloudwatchMetrics } from "../lib/cloudwatch-metrics";
import { SecurityHQ } from "../lib/security-hq";

const app = new App();

new SecurityHQ(app, "security-hq", {
  stack: "security",
  stage: "PROD",
  cloudFormationStackName: "security-hq-PROD",
  env: { region: "eu-west-1" },
  buildIdentifier: process.env["BUILD_NUMBER"] ?? "DEV",
});

new CloudwatchMetrics(app, "cloudwatch-metrics", {
  stack: "security",
  stage: "PROD",
  cloudFormationStackName: "cloudwatch-metrics-PROD",
  env: { region: "eu-west-1" },
});
