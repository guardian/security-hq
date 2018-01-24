# SSM 

## Prerequisites for this tutorial

 * [jq](https://stedolan.github.io/jq/) - the command line json manipulation program
 * [aws](https://docs.aws.amazon.com/cli/latest/userguide/installing.html) - the command line Amazon tool (awscli)
 * some understanding of bash
 * some understanding of IAM, EC2 and Cloudformation

OS X users can download jq and awscli using [Homebrew](https://brew.sh/):

```
brew install jq
brew install awscli

```


## Setting up a instance with EC2run

If you have already setup EC2 run on the instance, then you can skip to the Commands section.

### Create IAM client permissions

Use an admin account, then you can skip this. If not, see Task 1 of the [AWS documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-access.html#sysman-access-user).


### Installation

#### Create an IAM role (this will need to be done in CloudFormation)

See Task 2 in the [AWS documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-access.html#sysman-configuring-access-role).

In short, assuming you have an instance role already, you will need to include the following yaml entry under the [instance properties](https://github.com/guardian/security-hq/blob/master/cloudformation/security-hq.template.yaml#L86):

```
ManagedPolicyArns: [ "arn:aws:iam::aws:policy/service-role/AmazonEC2RoleforSSM" ]
```

#### Launch a box

Launch a clean box with the above role (or apply the role afterwards).

Install (this bit will need to be done in launch config or config management)

```
sudo apt-get update
sudo apt-get install amazon-ssm-agent
```

OR

```
wget https://s3.amazonaws.com/ec2-downloads-windows/SSMAgent/latest/debian_amd64/amazon-ssm-agent.deb
mkdir /tmp/ssm
sudo dpkg -i amazon-ssm-agent.deb
```

then log off

#### Check you now have access to the box via ssm

Choose the target account and region.  For example:

```
export region="eu-west-1"
export profile="security"
```

The box id should be present in the following command output:

```
aws --region $region --profile $profile ssm describe-instance-information
```

Note that there is a very limited set of items on which you can filter the output.
This list does not include arbitrary tags.  If you wish to find the list of instances with
specific tags, do that first using the `ec2 describe-instances` command, then execute the 
command against the resulting set of instance ids:

```
aws --region $region --profile $profile ec2 describe-instances
```


## Commands 

The AWS documentation has [a larger tutorial](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/tutorial_run_command.html) on using Run Command. However, exporting a `region`, `profile`, `command` and then using the following convenience functions will cover most purposes.

### Convenience functions

To help with running commands, we can create several functions, and save them as a shell script. 

An [example script](https://git.io/vNv6w) can be found in the Security HQ repository. Here are the functions it contains:

```
function send_command {
aws --region $region --profile $profile ssm send-command \
   --document-name "AWS-RunShellScript" \
   --comment "Shell command by $(whoami)" \
   --instance-ids $instance \
   --parameters commands="$command" \
   | jq -r '.Command.CommandId'
}
```

```
function read_command_output {
aws --region $region --profile $profile ssm list-command-invocations \
   --command-id "$command_id" \
   --details \
   | jq -r '.CommandInvocations[].CommandPlugins[].Output'
}
```

```
function wait_for_command {
result=0
for this_instance in $instance; do
   responseCode=-1
   printf "$this_instance:"
   while [[ $responseCode -eq -1 ]]; do
      printf "."
      sleep 1
      responseCode=$(aws --region $region --profile $profile ssm list-command-invocations \
         --command-id "$command_id" \
          --instance-id "$this_instance" \
         | jq -r '.ResponseCode')
      if [[ $responseCode -ne -1 ]]; then
         echo;
      fi
      if [[ $responseCode -gt -0 ]]; then
         result=1
      fi
   done
done
return $result
}
```

```
function run {
    command_id=$(send_command)
    wait_for_command
    echo "Return code was $?"
    read_command_output
}

run
```


### Run a command

Specify the command and target:

```
export command="uname -a"
export instance="i-0fe40a72847b61cc6"
```

or

```
export instance="i-01cfb366185e459bd i-0fe40a72847b61cc6"
```

Finally, run the script:

```
sh ssm-util.sh
```

The instance should respond with `Return code was 0`, followed by any output from the command sent.


## How the ssm-util script works

Details for the curious, not required reading.

#### Output the command id required for the next section:

```
command_id=$(send_command)
```

#### Wait for command to complete:

```
wait_for_command
```

#### Get the output:

```
read_command_output
```

#### Finally, do it all in one go!

```
function run {
    command_id=$(send_command)
    wait_for_command
    echo "Return code was $?"
    read_command_output
}

run
```



## References

1. [http://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-access.html](http://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-access.html)
2. [http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/tutorial_run_command.html](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/tutorial_run_command.html)
3. [https://docs.aws.amazon.com/cli/latest/userguide/installing.html](https://docs.aws.amazon.com/cli/latest/userguide/installing.html)
