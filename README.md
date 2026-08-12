# Security HQ

This project contains two lambdas, previously referred to as Credentials Reaper.

The original web app has been removed.  It also contains a `core` project of code shared by the lambdas

Instructions to run each lambda are found in the relevant documentation in the sub-project.

## Credentials Reaper actions.

The Credentials Reaper processes automatically disable permanent IAM users
with access keys that haven’t been rotated within 90 days for users with a password (human users)
or 365 days for users without a password (machine users).

It also disables permanent users who have left the Guardian.

The reaper sends email notifications to the AWS account the user is in, before disabling a user.
The emails are sent via Anghammarad and uses its AWS Account to email address mappings.

You can also find the dynamo table in the Security AWS Account.

# Local development

This project is suitable to run in a devcontainer.  Required files will be fetched by the setup script, and can be 
altered as desired.

There is a limitation to the accounts which can be checked, as we don't want to hand extensive power to the DEV environment
to assume roles in real accounts.

### Requirements
1. Java 21. See [.tool-versions](.tool-versions) for the exact version. [mise](https://mise.en.dev/) is the recommended Java version manager.
2. [Docker](https://docs.docker.com/desktop/install/mac-install/).
3. [dev-nginx](https://github.com/guardian/dev-nginx).
4. AWS credentials for the `security` profile.

> **Note**
> Guardian Engineers can use credentials from Janus.
> External engineers can use the [CloudFormation template](cloudformation/security-test-user.yaml) to provision an IAM user, and create an access key separately.

### Setup
1. Ensure requirements are met. See above.
2. Run the setup script:

   ```bash
   ./script/setup
   ```

### Running locally
1. See lambda documentation

### Adding additional AWS accounts for local development
When running Security HQ locally, you can modify the list of AWS accounts to include additional accounts.
For example, you may want to add a specific account for debugging purposes. 
You will need valid AWS credentials for any accounts you wish to include.

To add a new AWS account, go to `~/.gu/security-hq/security-hq.local.conf`,
add a new object to the `AWS_ACCOUNTS` list, like this Deploy Tools account example:

```
AWS_ACCOUNTS = [
  {
   name = "Deploy Tools"
   id = "deployTools"
   roleArn = ""
  }
]
```

The value of `id` should be the same as the AWS Profile name, which you can see when you copy your credentials from Janus.
You can add a `roleArn` if you want to generate an IAM report, otherwise you don't need it.

### AWS Security Policies
See `watched-account` template under `cloudformation` folder for the security policies needed to run security-hq.

##### Checking CloudFormation

The aws cli can perform some basic template validation.

It requires AWS credentials to run, and can validate a single file like so:

`aws cloudformation validate-template --template-body file:///${PWD}/cloudformation/security-test-user.yaml --profile <AWS_PROFILE>`

[CFN nag](https://github.com/stelligent/cfn_nag) is a linting tool for CloudFormation templates that can help catch security issues.

If you have it installed, you can run:

`cfn_nag_scan --input-path cloudformation/*`

