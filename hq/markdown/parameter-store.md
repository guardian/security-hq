# SSM Parameters

This is a secure process for writing and reading (and auditing reads of) sensitive information.

Using SSM Parameter store provides a trivial way to push sensitive information into AWS systems
without GPG and/or S3 file stores.  

## Prerequisites for this tutorial

 * aws - the command line amazon tool
 * trivial bash knowledge
 * Some Scala knowledge

We will use the most trivial example: a String.

## Installation

Choose the target account and region.  For example:
```
export region="eu-west-1"
export profile="security"
```

## Create Parameters

For Configraun compatibility, keys should have the form: `/$stack/$app/$stage/domain/value`

```
aws --region $region --profile $profile ssm put-parameter --name '/mystack/myapp/PROD/snyk/token' --value 'mysnyktoken' --type String
```

Ideally, at create time, each key will be given a regex defining allowed values.  This will help prevent overwriting with 
bad values later.

## List Parameters

```
aws --region $region --profile $profile ssm describe-parameters
```

## List a Specific Parameter

```
aws --region $region --profile $profile ssm get-parameter --name /justin/testing
```

## Delete a Specific Parameter

```
aws --region $region --profile $profile ssm delete-parameter --name '/mystack/myapp/PROD/snyk/token' 

```

## Fetch Parameters from Application

See [Configraun](https://github.com/guardian/configraun) read me.

