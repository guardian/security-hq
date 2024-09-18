import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { SecurityHQ } from "./security-hq";

describe("HQ stack", () => {
  it("matches the snapshot", () => {
    const app = new App();
    const stack = new SecurityHQ(app, "security-hq", {
      stack: "security",
      stage: "PROD",
      buildIdentifier: "TEST"
    });
    expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
  });
});
