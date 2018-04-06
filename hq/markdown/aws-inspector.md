# AWS Inspector

[AWS Inspector](https://aws.amazon.com/inspector/) is an automated scanning system to detect poor configuration, 
vulnerable OS modules and packages, and poor runtime behaviour.

It is provided as standard to all AWS accounts.

## Usage

Simply add the AWS Inspector agent to your boxes.

The easiest way to do this is to add the `aws-inspector` role to your application's Amigo recipe.

## Implementation

An application has been created, which will:

  1. Create a lambda to
      1. Search for App, Stack and Stage tags on instances in the target account
      2. Create a resource group, assessment target, and assessment template for each combination, unless they already exist
      3. Limit the resource group to a maximum of five instances
      4. Start an Assessment run of one hour for each target
  2. Schedule the lambda for 3am on Mondays and Thursdays.

### Repo

See https://github.com/guardian/inspector-lambda

### Artifact

The artifact required by the lambda will be stored in a generic bucket owned by the `deployTools` account
for the purpose of distributing code used by multiple guardian accounts:

```
s3://guardian-dist/guardian/PROD/inspectory-lambda.jar
```

New versions of this application are published by pushing to that location.

### Stack Set

This lambda will be deployed into each account using a stackset.  The cloudformation is in the inspector-lambda repo 
under /cloudformation

## Output

An AWS Inspector run will be started for every combination of app, stack and stage present in the account, 
including 'not present', in the early hours of Mondays and Thursdays.

The results can be inspected and mitigated by the teams responsible.

## Implementation Details

### Agent

In order to track instance behaviour, an agent must be installed on the target instances.  

In theory, Amigo could do this for you with the `aws-inspector` role.  However, it appears that
AWS Inspector's upgrade process does not cope with changing kernel versions (we have a support
request to examine and resolve this problem).  Therefore it is currently necessary to install 
AWS Inspector as part of the cloud-init process by adding the following lines:

```
/usr/bin/wget https://d1wk0tztpsntt1.cloudfront.net/linux/latest/install
/bin/bash install
```


Alternatively see [here](https://docs.aws.amazon.com/inspector/latest/userguide/inspector_installing-uninstalling-agents.html#install-linux).

### Targeting

Instances are targeted based on tags.

Currently, AWS have implemented a 'match any' strategy on tag filters, which is of course, insane. As a result, including
Stack=XXX, App=YYY, Stage==CODE will match all instances with Stage 'CODE' _even if they do not match the other tags_.

As a result, the lambda will dynamically add another tag to each target instance with name `Inspection`, 
and value 'AWSInspection-$stack-$app-$stage'. This is then used to target individual runs.

### Cost

Currently, 30c per instance per scan.  This is relatively high, especially on highly scaled out services.  For this reason, the lambda will choose up to five instances to scan.

