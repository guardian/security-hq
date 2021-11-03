# Infrastructure

This directory defines the components to be deployed to AWS

## Useful commands

We follow the [`script/task`](https://github.com/github/scripts-to-rule-them-all) pattern,
find useful scripts within the [`script`](./script) directory for common tasks.

- `./script/setup` to install dependencies
- `./script/tsc [-w]` to compile the Typescript and check for errors
- `./script/test [-w]` wrapper to lint, and run tests
- `./script/lint [-fix]` to lint the code using ESLint
- `./script/generate` to build a CDK stack into the `cdk.out` directory
- `./script/ci` to lint and run tests, and generate templates of the CDK stacks
- `./script/diff` to print the diff between a traditional CloudFormation template and a CDK stack

`-w` is supported as an optional 'watch' flag for various commands.
`-fix` is supported as 'fix' for linting.
