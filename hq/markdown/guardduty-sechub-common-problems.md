This document aims to help out with tackling the long lists of vulnerabilities in Security Hub and GuardDuty. If you're
stuck on something that isn't listed here please get in touch with DevX and we'll get this doc updated with recommendations.

# AWS Security Hub Common Issues
AWS is pretty good at making 'remediation suggestions' for a lot of this stuff. Bear in mind that you should use CDK/cloudformation
where possible to make infrastructure changes rather than doing stuff in the console as suggested in the remediation instructions. 

## S3 buckets should prohibit public read access
This is covered by Security HQ as well. Generally public read access is discouraged as it allows anyone to read the data
in a bucket. Whilst the data in it right now may not be secret, in the future it could be, so best to lock buckets down.

However, there are quite a few scenarios where public access might be required.

## Buckets serving assets for the website e.g. images, static files.
You should *never* use an S3 bucket to serve public assets to users directly - instead you should use a CDN such as Fastly
or CloudFront. If you are using CloudFront, it should be straightforward to make your bucket private so that it can
only be accessed via CloudFront.

If you are using Fastly, setting up [authentication to a private bucket](https://docs.fastly.com/en/guides/amazon-s3#using-an-amazon-s3-private-bucket)
is possible, but quite complicated, involving permanent AWS IAM credentials ending up in VCL. If you don't make the bucket
private, you might consider adding some cloudwatch alarms to warn you of e.g. unexpectedly high S3 costs arising from 
the public bucket.

## RDS DB Instances should prohibit public access
Fix this! There's no reason for an RDS instance to be publicly accessible - you should lock it down to access only from
the EC2 instances or lambda functions that require access.

## The VPC default security group should not allow inbound and outbound traffic
This is an awkward one. In a whole 5 minutes of googling Phil couldn't work out how to set the inbound/outbound rules for a 
VPC's default security group via cloudformation. So our current suggestion is that you follow [the instructions](https://docs.aws.amazon.com/securityhub/latest/userguide/securityhub-standards-fsbp-controls.html#ec2-2-remediation)
to shut down the default security group via the console. BUT FIRST! [Make sure it's not being used by anything](https://aws.amazon.com/premiumsupport/knowledge-center/ec2-find-security-group-resources/)
Using the default security group is considered bad practice.

## S3 buckets should require requests to use Secure Socket Layer
This is a very common problem. Unfortunately AWS's [suggested fix](https://docs.aws.amazon.com/securityhub/latest/userguide/securityhub-standards-fsbp-controls.html#s3-5-remediation)
involves a bucket policy which would need setting for every bucket. We're hoping in future this is something we will be 
able to enforce organisation wide rather than having to be specified for every bucket. In the meantime you might choose
to focus on public buckets or ones containing highly sensitive data. Note that you should cloudform bucket policies
rather than modifying them in the console where possible.

## EC2 instances should use IMDSv2
Ths is likely to affect pretty much every instance across our estate as the default option for cloudformation is wrong :(
To fix this you should update the MetadataOptions of your launch configuration/launch template to set `HttpTokens` to `required`.
There's an example PR for this here https://github.com/guardian/amigo/pull/544

If you're lucky this won't impact your services, but there's a good chance you'll have to make changes before switching to
IMDSv2. If you're using `curl` to fetch metadata from the `169.254.169.254` IP address then you'll need to update those calls
prior to enforcing IMDSv2. You can see the difference between IMDSv1 and IMDSv2 calls [here](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instancedata-data-retrieval.html)

You will also experience problems with this change if you are using the instance metadata service within your app via an old 
version of the AWS SDK. You will need to be using at least version 1.11.678 or 2.10.20 of the AWS SDK to get IMDSv2 support. 
If you are using the guardian [simple configuration library](https://github.com/guardian/simple-configuration) you'll need 
to make sure you're on the latest version.

A quick test to see how painful this change will be is to search for usages of `169.254.169.254` in your repo/infrastructure.

If you are using AWS CDK you (may) need to wait before you can fix this feature - there's an issue tracking this 
[here](https://github.com/aws/aws-cdk/issues/5137)

# AWS GuardDuty Common Issues
Right now, we don't have any of these to suggest remediation for. Please get in contact with DevX if you're unsure about
something GuardDuty has flagged up
