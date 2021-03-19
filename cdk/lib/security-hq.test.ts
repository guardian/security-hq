import "@aws-cdk/assert/jest";
import { SynthUtils } from "@aws-cdk/assert";
import { App } from "@aws-cdk/core";
import { Security } from "./security-hq";

describe("The Security stack", () => {
  it("matches the snapshot", () => {
    const app = new App();
    const stack = new Security(app, "SecurityHQ", { stack: "security" });
    expect(SynthUtils.toCloudFormation(stack)).toMatchSnapshot();
  });
});
