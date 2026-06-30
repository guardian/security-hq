import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { CloudwatchMetrics } from "./cloudwatch-metrics";

describe("CloudwatchMetrics stack", () => {
    it("matches the snapshot", () => {
        const app = new App();
        const stack = new CloudwatchMetrics(app, "cloudwatch-metrics", {
            stack: "security",
            stage: "PROD",
            env: { region: "eu-west-1" },
        });
        expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
    });
});
