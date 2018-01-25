# Temporary SSH

In order to move towards use of temporary credentials, a facility has been provided to locally generate a keypair and push the
public key to the instance of your choice, all using your own AWS credentials.  A standard ssh command can then be used to 
connect to the instance, within a timeout period.

After the timeout period, the public key is removed from the instance and the private key is therefore useless and can be 
deleted.

The box will be marked as 'tainted' both in the motd login screen and via a tag on the instance.   'Tainted' instances should
be terminated at the end of a support process and replaced with cleanly built instances to prove that the build is good 
without manual intervention.

## Implementation

The mechanism is via AWS run-command.  Please see AWS documentation for details.

## Pre-requisites

The instance must 

  * be set up to use [run-command](run-command)
  * have sshd running
  * have a public IP address
  * have ssh access permitted (port 22 open)

# Download

The application is available to download at https://github.com/guardian/ssm-scala/
