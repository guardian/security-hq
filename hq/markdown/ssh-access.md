# SSH Access

[SSM-Scala](https://github.com/guardian/ssm-scala) is a tool executing commands on EC2 instances authenticated by IAM credentials.
It can be used to establish SSH sessions, or use EC2 Run Command to execute commands without an interactive shell.

## Usage

The project's README [contains detailed instructions for using the tool with your projects](https://github.com/guardian/ssm-scala#how-to-use-ssm-scala-with-your-own-project).

In summary:

  * Add EC2's SSM agent using one of the following approaches
    + include the `ssm-agent` Amigo role
    + refer to [the AWS documentation for installing the agent](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-install-ssm-agent.html)
  * Ensure AWS has permission to execute commands on the instance using one of the following approaches
    + add the AWS managed policy to your instance profile
    + add the specific required permissions to your instance profile
  * have sshd running
  * have ssh access permitted (port 22, ideally locked down to an appropriate IP range)

## Download

The application is available to download from the repository [github](https://github.com/guardian/ssm-scala/),
or via homebrew.
