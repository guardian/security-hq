# Running the lambda locally.

There is a provided Main file which invokes the same function as the lambda, but takes parameters from the command line instead of environment variables.

It can be invoked with sbt:

```
sbt 'iamOutdatedCredentials / run'
```

# Parameters for local run

 * Stack (can be anything)
 * Stage (should be DEV)
 * Dry run flag
 * S3 bucket
 * S3 conf file key

# Dev considerations

## Config

The provided config file does not contain all the accounts that the production config has, because we do not want to create
trust relationships for AssumeRole for every user.

## Database

The expected database for DEV is a local docker, which can be started with the provided docker compose file.

