package com.gu.hq

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider}
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2._
import com.amazonaws.services.elasticloadbalancing.{AmazonElasticLoadBalancingAsync, AmazonElasticLoadBalancingAsyncClient}
import com.amazonaws.services.elasticloadbalancing.model.{DescribeLoadBalancersRequest, DescribeLoadBalancersResult}
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.securitytoken.{AWSSecurityTokenServiceAsync, AWSSecurityTokenServiceAsyncClient}
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest


object AWS {
  def deployToolsCredentialsProviderChain = new AWSCredentialsProviderChain(
    DefaultAWSCredentialsProviderChain.getInstance(),
    new ProfileCredentialsProvider("deployTools")
  )

  def securityCredentialsProviderChain = new AWSCredentialsProviderChain(
    DefaultAWSCredentialsProviderChain.getInstance(),
    new ProfileCredentialsProvider("security")
  )

  def developerPlaygroundCredentialsProviderChain = new AWSCredentialsProviderChain(
    DefaultAWSCredentialsProviderChain.getInstance(),
    new ProfileCredentialsProvider("developerPlayground")
  )

  // EC2
  def ec2client(region: Regions): AmazonEC2 = {
    AmazonEC2AsyncClientBuilder.standard().withRegion(region).withCredentials(securityCredentialsProviderChain).build()
  }

  // ELBs
  def elbClient(region: Regions) = {
    AmazonElasticLoadBalancingAsyncClient.asyncBuilder().withRegion(region).withCredentials(securityCredentialsProviderChain).build()
  }

  // SNSs
  def snsClient(region: Regions) = {
    AmazonSNSAsyncClient.asyncBuilder().withRegion(region).withCredentials(developerPlaygroundCredentialsProviderChain ).build()
  }
  // STS
  def stsClient(region: Regions) = {
    AWSSecurityTokenServiceAsyncClient.asyncBuilder().withRegion(region).withCredentials(securityCredentialsProviderChain ).build()
  }

  def describeLoadBalancers(elbClient: AmazonElasticLoadBalancingAsync): DescribeLoadBalancersResult = {
    val request = new DescribeLoadBalancersRequest()
    elbClient.describeLoadBalancers(request)
  }

  def accountNumber(stsClient: AWSSecurityTokenServiceAsync) = {
    stsClient.getCallerIdentity(new GetCallerIdentityRequest()).getAccount
  }
}
