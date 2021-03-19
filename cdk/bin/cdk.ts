import "source-map-support/register";
import { App } from "@aws-cdk/core";
import { Security } from "../lib/security-hq";

const app = new App();
new Security(app, "SecurityHQ", {
  stack: "security",
  migratedFromCloudFormation: true,
});
