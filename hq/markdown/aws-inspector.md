# AWS Inspector

[AWS Inspector](https://aws.amazon.com/inspector/) is an automated scanning system to detect poor configuration, vulnerable OS modules and packages, and
poor runtime behaviour.

It is provided as standard to all AWS accounts.

## Implementation

An application has been created, which will:

  * Create a lambda to
    * Search for App, Stack and Stage tags on instances in the target account
    * Create a resource group, assessment target, and assessment template for each combination, unless they already exist
    * Limit the resource group to a maximum of five instances
    * Start an Assessment run of one hour for each target
  * Schedule the lambda for 3am on Mondays and Thursdays.

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

An AWS Inspector run will be started for every combination of app, stack and stage present in the account, in the early
hours of Mondays and Thursdays.

These can be inspected and mitigated by the teams responsible.

## Implementation Details

### Agent

In order to track instance behaviour, an agent must be installed on the target instances.  

Amigo will do this for you with the `aws-inspector` role.
Alternatively see [here](https://docs.aws.amazon.com/inspector/latest/userguide/inspector_installing-uninstalling-agents.html#install-linux).

### Targeting

Instances are targeted based on tags.

Currently, AWS have implemented a 'match any' strategy on tag filters, which is of course, insane.  

### Cost

Currently, 30c per instance per scan.  This is relatively high, especially on highly scaled out services.

