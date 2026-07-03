import "source-map-support/register";
import { App } from "aws-cdk-lib";
import { VulnerabilityMetrics } from "../lib/vulnerability-metrics";
import { SecurityHQ } from "../lib/security-hq";

const app = new App();

new SecurityHQ(app, "security-hq", {
  stack: "security",
  stage: "PROD",
  cloudFormationStackName: "security-hq-PROD",
  env: { region: "eu-west-1" },
  buildIdentifier: process.env["BUILD_NUMBER"] ?? "DEV",
});

new VulnerabilityMetrics(app, "vulnerability-metrics", {
  stack: "security",
  stage: "PROD",
  cloudFormationStackName: "vulnerability-metrics-PROD",
  env: { region: "eu-west-1" },
});
