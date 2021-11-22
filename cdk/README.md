# Infrastructure

This directory defines the components to be deployed to AWS.

## Useful commands

We follow the
[`script/task`](https://github.com/github/scripts-to-rule-them-all) pattern,
find useful scripts within the [`script`](./script) directory for common tasks.

- `./script/setup` to install dependencies
- `./script/build [--watch]` to compile the Typescript and check for errors
- `./script/test [-u] [--watch]` wrapper to lint, and run tests
- `./script/lint [--fix]` to lint the code using ESLint
- `./script/generate` to build a CDK stack into the `cdk.out` directory
- `./script/ci` to lint and run tests, and generate templates of the CDK stacks
- `./script/diff` to print the diff between a traditional CloudFormation
  template and a CDK stack

`-u` updates the snapshot tests, which is needed for any change in the cdk.
`--watch` is supported as an optional 'watch' flag for various commands.
`--fix` is supported as 'fix' for linting.

## Deployment

There are two stacks:

    SecurityVpc - the security account VPC
    SecurityHQ - the EC2 app, load balancer, etc.

`SecurityHQ` is continuously built and deployed, but `SecurityVpc` needs to be
synthed locally and changes committed because it is
['environment-aware'](https://docs.aws.amazon.com/cdk/latest/guide/environments.html).

You can do this by:

    $ ./script/generate security-vpc

(Janus credentials for the security account are required for this to work.)

As `SecurityHQ` depends on the output of `SecurityVpc`, if creating a new
staging environment, you will need to deploy only the VPC step in Riffraff
initially and use the resulting VPC ID to configure the `SecurityHQ` stack
before you can do a full deploy of both.

If/when the VPC is used for other apps it would make sense to move the
`SecurityVpc` stack into another repo and deploy it at a separate cadence rather
than for every Security HQ update.
