#!/usr/bin/env bash
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

function run {
    command_id=$(send_command)
    wait_for_command
    echo "Return code was $?"
    read_command_output
}

run