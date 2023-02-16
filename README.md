# Security HQ
Centralised security information for AWS accounts.

## Security HQ webapp
This webapp presents the primary interface for Security HQ.

The `watched-account` CloudFormation template will create ConfigRules
that monitor the status of other AWS accounts. This application
presents the data collected by those processes.

It also provides an interface on some markers of a watched AWS
account's health from a security point of view.

## Local development
### Requirements
1. Java 11. See [.tool-versions](.tool-versions) for the exact version. [asdf](https://asdf-vm.com/) is the recommended Java version manager.
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
1. Ensure requirements are met. See above.
2. Ensure project has been setup. See above.
3. Run the start script:
   
   ```bash
   ./script/start
   ```
4. Open [https://security-hq.local.dev-gutools.co.uk/](https://security-hq.local.dev-gutools.co.uk/)

### Adding additional AWS accounts for local development
When running Security HQ locally, you can modify the list of AWS accounts to include additional account.
For example, you may want to add a specific account for debugging purposes. 
You will need valid AWS credentials for any accounts you wish to include.

It's really easy to add a new AWS account! Go to `~/.gu/security-hq.local.conf`,
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

## Introduction to Security HQ's features

### Credentials Reaper
The Credentials Reaper is a feature in Security HQ which automatically disables permanent IAM users
with access keys that haven’t been rotated within 90 days for users with a password (human users)
or 365 days for users without a password (machine users).
It also disables permanent users who have left the Guardian.

The reaper sends email notifications to the AWS account the user is in, before disabling a user.
The emails are sent via Anghammarad and uses it's AWS Account to email address mappings.

You can also find the dynamo table in the Security AWS Account.

### Lambda
Security HQ holds a Lambda, which checks for security groups that are open to the world, except ELB groups. This data is used for `https://security-hq.gutools.co.uk/security-groups`.

It is deployed as a stack set and is defined in  `cloudformation/watched-account.template.yaml`.
This lambda is deployed manually by creating a JAR file locally and uploading it to S3: `s3://guardian-dist/guardian/PROD/securitygroups-lambda/`. The version name is important, because
the cloudformation has a paramter, `version`, which is used to locate the correct S3 file. The stackset is then deployed manually again to all accounts from the root account.

The build and deploy process is manual at present. The lambda was deployed once in 2018 and hasn't been updated since. Health tickets have been put onto the backlog to try to improve this process.

