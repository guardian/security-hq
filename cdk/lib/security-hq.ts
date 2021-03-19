import type { App } from "@aws-cdk/core";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuParameter, GuStack, GuStringParameter } from "@guardian/cdk/lib/constructs/core";

export class Security extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    const parameters = {
      SecurityHQSourceBundleBucket: new GuStringParameter(this, "SecurityHQSourceBundleBucket", {
        description: "S3 bucket containing Security HQ's source bundle",
      }),
      VpcId: new GuParameter(this, "VpcId", {
        type: "AWS::EC2::VPC::Id",
        description: "ID of the VPC Security HQ will run in",
      }),
      Subnets: new GuParameter(this, "Subnets", {
        description: "The subnets in which Security HQ will run",
        type: "List<AWS::EC2::Subnet::Id>",
      }),
      AMI: new GuParameter(this, "AMI", {
        description: "Base AMI for Security HQ instances",
        type: "AWS::EC2::Image::Id",
      }),
      TLSCertArn: new GuStringParameter(this, "TLSCertArn", {
        description: "ARN of a TLS certificate to install on the load balancer",
      }),
      AccessRestrictionCidr: new GuStringParameter(this, "AccessRestrictionCidr", {
        description: "CIDR block from which access to Security HQ should be allowed",
      }),
      // Your parameter looks similar to GuStageParameter. Consider using that instead.
      Stage: new GuStringParameter(this, "Stage", {
        description: "Application stage (e.g. PROD, CODE)",
        allowedValues: ["PROD", "CODE", "DEV"],
      }),
      LoggingRoleToAssumeArn: new GuStringParameter(this, "LoggingRoleToAssumeArn", {
        description: "Name of IAM role in logging account e.g. arn:aws:iam::222222222222:role/LoggingRole",
      }),
      InstanceType: new GuStringParameter(this, "InstanceType", {
        description: "AWS instance type (e.g. t4g.large)",
      }),
    };
  }
}
