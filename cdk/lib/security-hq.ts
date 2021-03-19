import { Peer } from "@aws-cdk/aws-ec2";
import type { App } from "@aws-cdk/core";
import { CfnOutput } from "@aws-cdk/core";
import { Stage } from "@guardian/cdk/lib/constants";
import { GuAutoScalingGroup, GuUserData } from "@guardian/cdk/lib/constructs/autoscaling";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import {
  GuDistributionBucketParameter,
  GuPrivateConfigBucketParameter,
  GuStack,
} from "@guardian/cdk/lib/constructs/core";
import type { AppIdentity } from "@guardian/cdk/lib/constructs/core/identity";
import { GuSecurityGroup, GuVpc, GuWazuhAccess } from "@guardian/cdk/lib/constructs/ec2";
import { GuAssumeRolePolicy, GuInstanceRole } from "@guardian/cdk/lib/constructs/iam";
import { GuHttpsClassicLoadBalancer } from "@guardian/cdk/lib/constructs/loadbalancing";
import { GuardianNetworks } from "@guardian/private-infrastructure-config";

export class Security extends GuStack {
  private appIdentity: AppIdentity = { app: "security-hq" };

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    const vpc = GuVpc.fromIdParameter(this, "vpc");
    const subnets = GuVpc.subnetsfromParameter(this);

    const role = new GuInstanceRole(this, {
      ...this.appIdentity,
      additionalPolicies: [new GuAssumeRolePolicy(this, "SecurityHQAssumeRolePolicy", { resources: ["*"] })],
    });

    const userData = new GuUserData(this, {
      ...this.appIdentity,
      distributable: {
        bucket: this.getParam(GuDistributionBucketParameter.parameterName),
        fileName: `${this.appIdentity.app}.deb`,
        executionStatement: `dpkg -i /${this.appIdentity.app}/${this.appIdentity.app}.deb`,
      },
      configuration: {
        bucket: new GuPrivateConfigBucketParameter(this),
        files: [
          [this.stack, this.stage, this.appIdentity.app, "security-hq.conf"].join("/"),
          [this.stack, this.stage, this.appIdentity.app, "security-hq-service-account-cert.json"].join("/"),
        ],
      },
    });

    const asg = new GuAutoScalingGroup(this, "AutoScalingGroup", {
      ...this.appIdentity,
      overrideId: true,
      vpc,
      vpcSubnets: { subnets },
      role,
      userData: userData.userData,
      stageDependentProps: {
        [Stage.CODE]: {
          minimumInstances: 1,
        },
        [Stage.PROD]: {
          minimumInstances: 1,
        },
      },
      additionalSecurityGroups: [
        new GuWazuhAccess(this, "WazuhSecurityGroup", { vpc }),
        new GuSecurityGroup(this, "HTTP egress access", {
          vpc,
          allowAllOutbound: false,
          egresses: [{ range: Peer.anyIpv4(), port: 80, description: "Allow all outbound HTTP traffic" }],
        }),
      ],
    });

    const loadBalancer = new GuHttpsClassicLoadBalancer(this, "LoadBalancer", {
      vpc,
      crossZone: true,
      subnetSelection: { subnets },
      targets: [asg],
      listener: {
        allowConnectionsFrom: Object.entries(GuardianNetworks).map(([office, cidrRange]) => Peer.ipv4(cidrRange)),
      },
      propertiesToOverride: {
        AccessLoggingPolicy: {
          EmitInterval: 5,
          Enabled: true,
          S3BucketName: "gu-elb-logs",
          S3BucketPrefix: ["ELBLogs", this.stack, this.appIdentity.app, this.stage].join("/"),
        },
      },
    });

    new CfnOutput(this, "LoadBalancerUrl", {
      value: loadBalancer.loadBalancerDnsName,
    });
  }
}
