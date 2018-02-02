# Temporary SSH

This facility allows ssh to an instance using temporary credentials.

## Implementation

An ssh keypair is locally generated, and the public key pushed to the instance using your AWS credentials.

A standard ssh command can then be used to connect to the instance, within a timeout period.

After the timeout period, the public key is removed from the instance. The private key is then useless and can be
deleted.

The box will be marked as 'tainted' both in the motd login screen and via a tag on the instance.   'Tainted' instances should
be terminated at the end of a support process and replaced with cleanly built instances to prove that the build is good
without manual intervention.

The mechanism is via AWS run-command.  Please see AWS documentation for details.

## Pre-requisites

The instance must

  * be set up to use [run-command](run-command)
  * have sshd running
  * have a public IP address
  * have ssh access permitted (port 22 open)

# Download

The application is available to download at [github](https://github.com/guardian/ssm-scala/)
