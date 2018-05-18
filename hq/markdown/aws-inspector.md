# AWS Inspector

[AWS Inspector](https://aws.amazon.com/inspector/) is an automated scanning system that detects poor configuration, 
checks for vulnerable OS modules and packages, and inspects runtime behaviour for signs of compromise.
It is provided as standard to all AWS accounts.

We have deployed Lambdas across our AWS accounts that will autmatically run schduled inspections. 

## Usage

Simply add the AWS Inspector agent to your boxes.

The easiest way to do this is to add the `aws-inspector` role to your application's [Amigo](https://amigo.gutools.co.uk)
recipe.

Alternatively refer to the
[AWS instructions for installing the Inspector Agent](https://docs.aws.amazon.com/inspector/latest/userguide/inspector_installing-uninstalling-agents.html#install-linux).


## Reveiwing inspection runs

Security HQ gives an overview of the inspection run results across all accounts. For more detailed information about
the findings, take a look at AWS Inspector in your AWS Console. 

## Implementation

[Inspector Lambda](https://github.com/guardian/inspector-lambda)

Does the following:

  1. Create a lambda to
      1. Search for App, Stack and Stage tags on instances in the target account
      2. Create a resource group, assessment target, and assessment template for each combination, unless they already exist
      3. Limit the resource group to a maximum of five instances
      4. Start an Assessment run of one hour for each target
  2. Schedule the lambda for 3am on Mondays and Thursdays.

### Targeting

Instances are targeted based on their Stack, Stage and App tags. A scheduled assessment is created for every unique
combination of these tags.

To work around limitations of AWS Inspector, the lambda dynamically adds a tag to each target instance with
name `Inspection`, and value 'AWSInspection-$stack-$app-$stage'. This tag is used to target scheduled assessment
runs.

### Cost

Currently, 30c per instance per scan.  This is relatively high, especially on highly scaled out services.  For this reason, the lambda will choose up to five instances to scan.

