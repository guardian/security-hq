package com.gu.hq

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider}
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2._
import com.amazonaws.services.elasticloadbalancing.{AmazonElasticLoadBalancingAsync, AmazonElasticLoadBalancingAsyncClient}
import com.amazonaws.services.elasticloadbalancing.model.{DescribeLoadBalancersRequest, DescribeLoadBalancersResult}

object AWS {
  def credentialsProviderChain = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider(),
    new ProfileCredentialsProvider("security")
  )

  // EC2
  def ec2client(region: Regions): AmazonEC2 = {
    AmazonEC2AsyncClientBuilder.standard().withRegion(region).build()
  }

  // ELBs
  def elbClient(region: Regions) = {
    AmazonElasticLoadBalancingAsyncClient.asyncBuilder().withRegion(region).build()
  }

  def describeLoadBalancers(elbClient: AmazonElasticLoadBalancingAsync): DescribeLoadBalancersResult = {
    val request = new DescribeLoadBalancersRequest()
    elbClient.describeLoadBalancers(request)
  }
}
