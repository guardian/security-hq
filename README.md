Security HQ
===========

Centralised security information for AWS accounts.

Security HQ webapp
==================

This webapp presents the primary interface for Security HQ.

The `watched-account` CloudFormation template will create ConfigRules
that monitor the status of other AWS accounts. This application
presents the data collected by those processes.

It also provides an interface on some markers of a watched AWS
account's health from a security point of view.

## Development

### Getting started

### AWS Configuration

_Note: this section is only necessary if you do not already have another means of accessing your account(s) with the required permissions. Particularly, that is true for Guardian developers, who can use temporary credentials from Janus in the usual way._

The security-test-user CloudFormation template should create a user with all the required permissions.

Once the CloudFormation is complete and the user created, you will need to create an access key for this user.

In the console, select the user and then under `security credentials` click `create access key`.

You will be presented with an *Access key ID* and a *Secret access key* that you are going to use during the next step.

Create the file `~/.aws/credentials` with the following contents

```
[security-test]
aws_access_key_id = <Access key ID>
aws_secret_access_key = <Secret Access key>
```

If you already had this file, you should just add this section.

Create the entry below in `~/.aws/config` file.

```
[profile security-test]
region = eu-west-1   
```

### Other Configuration

There are two other files that you will need to run security HQ locally. These are:

1.  `~/.gu/security-hq.local.conf` (Please update the value of key `GOOGLE_SERVICE_ACCOUNT_CERT_PATH`)
1. `~/.gu/security-hq-service-account-cert.json`

Both of these files can be found in the Security AWS account in S3 here `s3://security-dist/security/PROD/security-hq/`.

To access the Security AWS account you will need to raise a PR in [Janus](https://github.com/guardian/janus/blob/main/guData/src/main/scala/com/gu/janus/data/Access.scala) 
to get access to the Security account (`Security.dev` should be appropriate).

Once you have Janus credentials for the AWS Security account, you can copy the files from S3 by using the following:
```
 aws s3 cp s3://security-dist/security/PROD/security-hq/security-hq.local.conf ~/.gu --profile security
```

### Adding additional AWS accounts for local development

When running security HQ locally, you can modify the list of AWS accounts to include additional account. 
For example, you may want to add a specific account for debugging purposes. You will need valid AWS credentials for any accounts you wish to include.

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

The value of `id` should be the same as the AWS Profile name, which you can see when you copy your credentials from Janus. You can add a `roleArn` if you want to generate an IAM report, otherwise you don't need it.

### AWS Security Policies
See `watched-account` template under `cloudformation` folder for the security policies needed to run security-hq.

### nginx setup

When running security-hq locally you will always need nginx running in the background. You only need to configure dev-nginx once (as described in step 2), but if you restart your computer, you will need to run `sudo nginx` to restart the nginx process.

To check nginx is running, you can run `ps -e | grep nginx` and you should receive a response that includes `nginx: master process nginx` and `nginx: worker process`.

1. Install dev-nginx:

[dev-nginx](https://github.com/guardian/dev-nginx) contains a number of generic useful scripts to:
- issue certs locally (and automatically trust them)
- [generate an nginx config file](https://github.com/guardian/dev-nginx#setup-app) from a yaml definition

MacOS users:

```bash
brew tap guardian/homebrew-devtools
brew install guardian/devtools/dev-nginx
```

There are further instructions in the [dev-nginx](https://github.com/guardian/dev-nginx) repo.

2. configure dev-nginx:

run each of these commands:

`dev-nginx add-to-hosts-file security-hq.local.dev-gutools.co.uk`

`dev-nginx setup-cert security-hq.local.dev-gutools.co.uk`

`dev-nginx setup-app nginx/nginx-mapping.yml`

1. To stop and restart nginx you can now run:

`dev-nginx restart-nginx`

4. Now when you run the project (see next step for details), it will also be accessible via [https://security-hq.local.dev-gutools.co.uk/](https://security-hq.local.dev-gutools.co.uk/)

### Running project
From the root of the project:

1. Get Security Janus credentials. 

2. Run sbt: `$ ./sbt`

3. Select the project that you want to run: `sbt:security-hq> project hq`

4. Start the application: `sbt:security-hq> run`

Once the sever has started, the webapp is accessible at [https://security-hq.local.dev-gutools.co.uk/](https://security-hq.local.dev-gutools.co.uk/)

### DynamoDB local setup

Running Security HQ locally requires a local instance of DynamoDb to connect
to. The `run` tasks handles most of the work automatically by starting and
stopping `DynamoDBLocal` as appropriate. It does not, however, create a table
for the application to use.

To do that, the following script needs to be executed _while_ the application
is running. This only needs to be done once, as the database is stored on disk
and persistent between application runs.

1. `$ ./script/setup`

### Working with CSS and JS

Security HQ uses [Prettier](https://prettier.io) and [ESLint](https://eslint.org/docs/about/) to provide opinionated code formatting and linting. As part of the automated build, all CSS and JS will be validated using the rules of Prettier and ESLint.

#### Testing changes, and passing code validation

Before beginning, you may want to install a node version manager, such as [nvm](https://github.com/creationix/nvm).

1. You will need to install [Yarn](https://yarnpkg.com) to handle the project dependencies:
	- Linux: Instructions can be found here: [https://yarnpkg.com/lang/en/docs/install/#linux-tab](https://yarnpkg.com/lang/en/docs/install/#linux-tab)
	- Mac OSX: `$ brew install yarn`

2. Then install all the dependencies by running:

`$ yarn`


##### Running checks with Yarn

To see any errors and warnings for the CSS use:

`$ yarn run sass-lint`

To see any errors and warnings for the JS use:

`$ yarn run eslint`

To attempt to auto-fix the CSS and JS, you can try using Prettier:

`$ yarn run prettier`

**N.B. Although Prettier will write to the files, changes will still need to be staged afterwards.**


##### Checking CloudFormation

The aws cli can perform some basic template validation.

It requires AWS credentials to run, and can validate a single file like so:

`aws cloudformation validate-template --template-body file:///${PWD}/cloudformation/security-test-user.yaml --profile <AWS_PROFILE>`

[CFN nag](https://github.com/stelligent/cfn_nag) is a linting tool for CloudFormation templates that can help catch security issues.

If you have it installed, you can run:

`cfn_nag_scan --input-path cloudformation/*`

## Introduction to Security HQ's features

### Credentials Reaper
The Credentials Reaper is a feature in Security HQ which automatically disables vulnerable permanent IAM users. 
It considers a vulnerable user to be a user that has a password, but no mfa and/or access keys which haven’t been rotated within 90 days for users with a password (human users) or 365 days for users without a password (machine users). 
It disables users in the following way: if there’s a password, it will be removed and if there are active access keys, they will be disabled.

The reaper sends email notifications to the AWS account the user is in, before disabling a user. The emails are sent via Anghammarad and uses it's AWS Account to email address mappings.

You can also find the dynamo table in the Security AWS Account. 

### Lambda
Security HQ holds a Lambda, which checks for security groups that are open to the world, except ELB groups. This data is used for `https://security-hq.gutools.co.uk/security-groups`.

It is deployed as a stack set and is defined in  `cloudformation/watched-account.template.yaml`. 
This lambda is deployed manually by creating a JAR file locally and uploading it to S3: `s3://guardian-dist/guardian/PROD/securitygroups-lambda/`. The version name is important, because
the cloudformation has a paramter, `version`, which is used to locate the correct S3 file. The stackset is then deployed manually again to all accounts from the root account.

The build and deploy process is manual at present. The lambda was deployed once in 2018 and hasn't been updated since. Health tickets have been put onto the backlog to try to improve this process.

