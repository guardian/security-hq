# Wazuh

## Introduction

Wazuh is a security monitoring service managed by Infosec. It requires agents be installed in our EC2 instances, which then report security events
back to Infosec's central wazuh server.


## Integrating Wazuh with your ec2 based applications

The Wazuh agent is available as an amigo role called `wazuh-agent`, which can be added to your application's amigo recipe. No config is required - the role
will correctly install and configure the agent in your image. The agent does however have requirements to run. Namely:

* Ports 1514 and 1515 open for outbound communication
* Permissions to read the instance's autoscaling group tags

Another detail to consider is that it's common for a single amigo recipe to be shared between many different services. In practice, this means
you won't be able to integrate wazuh one service at a time, but in batches.

So a roll out will typically include these steps:

1. Pick an amigo recipe (check `amiTags`/`Recipe` in riff-raff.yaml)
2. Find which services are using it (lookup [recipe usages](https://amigo.gutools.co.uk/recipes/bionic-java8-deploy-infrastructure/usages) in amigo)
3. Check if security groups and iam policy for each service meet requirements
4. Update the cloudformation stacks of those that don't
4. Add the wazuh-agent role to the recipe
5. Bake the image
6. Redeploy services
7. Test connectivity

### Outbound traffic on ports 1514 and 1515

The Wazuh agent communicates with the central server on port 1514 and 1515.

It's relatively uncommon, but some of our services have outbound traffic restricted in the instance's security group.
If your service is one of them, you'll need to open those ports. This can be done by patching the security group, or
by adding a second security group to the InstanceRole

Here's an [example](https://github.com/guardian/deploy-tools-platform/pull/313):

```yaml
  WazuhSecurityGroup:                 #<-- declare a new security group
    Type: AWS::EC2::SecurityGroup
    Properties:
      VpcId:
        Ref: VpcId
      SecurityGroupEgress:
      - IpProtocol: tcp
        FromPort: 1514
        ToPort: 1515
        CidrIp: 0.0.0.0/0


  InstanceRole:                      #<-- Find the instance role that needs to change
    Type: AWS::IAM::Role
    Properties:
      SecurityGroups:
      - Ref: InstanceSecurityGroup
      - Ref: WazuhSecurityGroup      #<-- add it to the instance role

```

#### VPC access control lists (ACLs)
You may find that your VPC doesn't allow traffic on ports 1514/1515. This isn't something we've come across so far but if 
you encounter problems setting up Wazuh this maybe something to check. Feeel free to contact Phil or Jorge in the 
DevX team for assistance - we want to get the agent installed but don't want to waste your time debugging awkward connectivity
problems!

### IAM policy for querying tags

The wazuh agent will identify itself with the central server using the app/stage/stack tags of the autoscaling group the instance belongs too.
This approach tends to be more reliable than querying for the instance's tags, since wazuh queries for them at instance boot.

We recommend adding a standard DescribeEC2Policy to the InstanceRole, which adds the required `autoscaling` permissions, and recommended
`ec2` ones as well.

```yaml
  InstanceRole:                      #<-- Note the name of the instance role that needs to change
    Type: AWS::IAM::Role
    Properties:
      SecurityGroups:
      - Ref: InstanceSecurityGroup
      - Ref: WazuhSecurityGroup

  DescribeEC2Policy:                 #<-- declare a new iam policy
    Type: AWS::IAM::Policy
    Properties:
      PolicyName: describe-ec2-policy
      PolicyDocument:
        Statement:
        - Effect: Allow
          Resource: "*"
          Action:
          - ec2:DescribeTags
          - ec2:DescribeInstances
          - autoscaling:DescribeAutoScalingGroups
          - autoscaling:DescribeAutoScalingInstances
      Roles:
      - !Ref InstanceRole             #<-- attach it to the instance role
```

### Adding the wazuh-agent role in amigo

This is the easy part. You edit your recipe, tick the box next to the wazuh-agent role, save, and bake!

## Testing

The system is ready to test once the new bake is finished, and the application is redeployed in riff raff. This will pick up the freshly baked ami with
the wazuh agent ready to go.

First thing to test is if your service is still running properly. It's highly unlikely that the agent will disrupt your service, but it's of course
worth checking.

Second, we'll want to see if the agent has booted and connected to the central server. Unfortunately, access to the wazuh UI is restricted due to the sensitive
nature of the data, so you'll either need to ask someone on DevX to check in the UI for you, or follow the steps below
to verify that everything's working.  

Luckily, it's easy to check success on the instance itself. At a high level, we need to

1. confirm that the wazuh-agent service is loaded and active
2. check the logs to verify the agent succesfully authenticated with the manager server

The quickest way to do this is usin [ssm-scala](https://github.com/guardian/ssm-scala) to run some commands on the instances
you want to check. Here's the full command:

`ssm cmd -c "service wazuh-agent status | grep Active && journalctl -u wazuh-agent | grep Valid" -t app,stage,stack -p <janus_profile>`

So, for example to check the wazuh status for *all instances tagged 'amiable'* I would run:

`ssm cmd -c "service wazuh-agent status | grep Active && journalctl -u wazuh-agent | grep Valid" -p deployTools  -t amiable`

In a happy scenario this should give you output looking a bit like this, telling you that the agent is running  and has
authenticated correctly.

```
========= i-040399xxxxxxx =========
STDOUT:
   Active: active (running) since Tue 2021-02-09 12:16:06 UTC; 6 days ago
Feb 09 12:15:58 ip-10-248-48-90 authenticate-with-wazuh-manager.sh[1060]: 2021/02/09 12:15:58 agent-auth: INFO: Valid key created. Finished.

STDERR:

========= i-01fc71000xxxxxx =========
STDOUT:
   Active: active (running) since Mon 2021-02-15 11:20:29 UTC; 6h ago
Feb 15 11:20:21 ip-10-248-48-147 authenticate-with-wazuh-manager.sh[1068]: 2021/02/15 11:20:21 agent-auth: INFO: Valid key created. Finished.

STDERR:
```

And that's it, all done! On to the next app. 

## Troubleshooting

If you don't see lines looking like this, you might wish to get rid of the 'grep' commands in the `ssm` command so as to
view the full wazuh agent logs, e.g.:

`ssm cmd -c "service wazuh-agent status && journalctl -u wazuh-agent" -p deployTools  -t amiable`

Below is an example of running the two commands separately whilst sshing into the box (the downside of using SSH is that 
you'll need to log into each box individually).  If your agent is having trouble booting or reaching the server, you can
 use the example below as a reference to see at which stage the failure happened.


```bash
$ ssm ssh -i i-0bb1210e80da0d168 -x -p deployTools
$ systemctl status wazuh-agent
● wazuh-agent.service - Wazuh agent
   Loaded: loaded (/etc/systemd/system/wazuh-agent.service; enabled; vendor preset: enabled)
   Active: active (running) since Fri 2021-02-05 13:52:47 UTC; 24min ago
    Tasks: 27
   Memory: 128.3M
      CPU: 25.896s
   CGroup: /system.slice/wazuh-agent.service
           ├─2650 /var/ossec/bin/ossec-execd
           ├─2661 /var/ossec/bin/ossec-agentd
           ├─2677 /var/ossec/bin/ossec-syscheckd
           ├─2704 /var/ossec/bin/ossec-logcollector
           └─2742 /var/ossec/bin/wazuh-modulesd
$ journalctl -u wazuh-agent
-- Logs begin at Fri 2021-02-05 13:52:28 UTC, end at Fri 2021-02-05 14:17:02 UTC. --
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + '[' -f /var/ossec/etc/authd.pass ']'
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + MANAGER_ADDRESS=wazuh.address.co.uk
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: ++ ec2metadata --availability-zone
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: ++ sed 's/.$//'
Feb 05 13:52:34 ip-10-248-51-91 systemd[1]: Starting Wazuh agent...
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + REGION=eu-west-1
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: ++ ec2metadata --instance-id
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + INSTANCE_ID=i-345345frgrg
Feb 05 13:52:35 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: ++ aws autoscaling describe-auto-scaling-instances --region eu-west-1 --instance-ids i-345345frgrg --output text --query 'AutoScalingInstances[0].AutoSca
Feb 05 13:52:37 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + ASG_NAME=AutoscalingGroup-MH1W6904Q5
Feb 05 13:52:37 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + ARGS='describe-auto-scaling-groups --region eu-west-1 --auto-scaling-group-name AutoscalingGroup-MH1W6904Q5 --output text'
Feb 05 13:52:37 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: ++ aws autoscaling describe-auto-scaling-groups --region eu-west-1 --auto-scaling-group-name AutoscalingGroup-MH1W6904Q5 --output text --query 'Au
Feb 05 13:52:38 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + APP=amigo
Feb 05 13:52:38 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: ++ aws autoscaling describe-auto-scaling-groups --region eu-west-1 --auto-scaling-group-name AutoscalingGroup-MH1WP6904Q5 --output text --query 'Au
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + STACK=deploy
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: ++ aws autoscaling describe-auto-scaling-groups --region eu-west-1 --auto-scaling-group-name AutoscalingGroup-MH1WP904Q5 --output text --query 'Au
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + STAGE=CODE
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + cp /var/ossec/etc/ossec.conf /var/ossec/etc/ossec.conf.bak
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + sed -i s/MANAGER_IP/wazuh.address.co.uk/ /var/ossec/etc/ossec.conf
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + sed -i 's/<protocol>udp<\/protocol>/<protocol>tcp<\/protocol>/' /var/ossec/etc/ossec.conf
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + cat
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + /var/ossec/bin/agent-auth -m wazuh.address.co.uk -A i-345345frgrg
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Started (pid: 1397).
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Starting enrollment process to server: wazuh.address.co.uk
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Connected to 12.12.12.12:1515
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Registering agent to unverified manager.
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Using password specified on file: /var/ossec/etc/authd.pass
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Using agent name as: i-345345frgrg
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Request sent to manager
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Waiting for manager reply
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Received response with agent key
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Valid key created. Finished.
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: 2021/02/05 13:52:39 agent-auth: INFO: Connection closed.
Feb 05 13:52:39 ip-10-248-51-91 authenticate-with-wazuh-manager.sh[1098]: + rm /var/ossec/etc/authd.pass
Feb 05 13:52:40 ip-10-248-51-91 env[1400]: Starting Wazuh v...
Feb 05 13:52:41 ip-10-248-51-91 env[1400]: Started ossec-execd...
Feb 05 13:52:42 ip-10-248-51-91 env[1400]: Started ossec-agentd...
Feb 05 13:52:43 ip-10-248-51-91 env[1400]: Started ossec-syscheckd...
Feb 05 13:52:44 ip-10-248-51-91 env[1400]: Started ossec-logcollector...
Feb 05 13:52:45 ip-10-248-51-91 env[1400]: Started wazuh-modulesd...
Feb 05 13:52:47 ip-10-248-51-91 env[1400]: Completed.
Feb 05 13:52:47 ip-10-248-51-91 systemd[1]: Started Wazuh agent.
```
A milestone to look out for is a successfull authentication with the central server

```
INFO: Valid key created. Finished.
```

And a successful boot of the agent

```
systemd[1]: Started Wazuh agent.
```

The logs show the authentication script running before the agent proper runs. You can read what the script does
in [amigo's repository](https://github.com/guardian/amigo/blob/14aec90b99bc1dc42e7c110e0f7e72edd7e80ead/roles/wazuh-agent/tasks/main.yml#L70).
