# SSM 

## Prerequisites for this tutorial

 * jq - the command line json manipulation program
 * aws - the command line amazon tool
 * some understanding of bash
 * some understanding of IAM, EC2 and Cloudformation

## Create IAM client permissions

Use an admin account, then you can skip this. If not, it is task 1 in doc 1 below

## Installation

Choose the target account and region.  For example:
```
export region="eu-west-1"
export profile="security"
```

### Create an IAM role (this will need to be done in cloudformation)

See Task 2 in doc 1 below

In short, assuming you have an instance role already, you will need to add the following yaml entry:
```
ManagedPolicyArns: [ "arn:aws:iam::aws:policy/service-role/AmazonEC2RoleforSSM" ]
```

### Launch a box

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

### Check you now have access to the box via ssm

The box id should be present in the following command output:
```
aws --region $region --profile $profile ssm describe-instance-information
```

Note that there is a very limited set of items on which you can filter the output.
This list does not include arbitrary tags.  If you wish to find the list of instances with
specific tags, do that first using the `ec2 describe-instances` command, then execute the 
command against the resulting set of instance ids.

### Commands 

### Convenience functions

See doc 2 below

Create the following functions:
```
function send_command {
aws --region $region --profile $profile ssm send-command \
   --document-name "AWS-RunShellScript" \
   --comment "Shell command by $(whoami)" \
   --instance-ids $instance \
   --parameters commands="$command" \
   | jq -r '.Command.CommandId'
}


function read_command_output {
aws --region $region --profile $profile ssm list-command-invocations \
   --command-id "$command_id" \
   --details \
   | jq -r '.CommandInvocations[].CommandPlugins[].Output'
}


function wait_for_command {
result=0
for this_instance in $instance; do
   responseCode=-1
   printf "$this_instance:"
   while [[ $responseCode -eq -1 ]]; do
      printf "."
      sleep 1
      responseCode=$(aws --region $region --profile $profile ssm get-command-invocation \
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

### Run a command

Specify the command and target:
```
export command="uname -a"
```
```
export instance="i-0fe40a72847b61cc6"
```
or
```
export instance="i-01cfb366185e459bd i-0fe40a72847b61cc6"
```

This command will output the command id required for the next section
```
export command_id=$(send_command)
```

### Wait for command to complete

```
wait_for_command
```

### Get the output

```
read_command_output
```

## Finally, do it all in one go!

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

1. http://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-access.html
2. http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/tutorial_run_command.html

