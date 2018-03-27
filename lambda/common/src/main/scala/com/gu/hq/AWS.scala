package com.gu.hq

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.{AmazonEC2AsyncClient, AmazonEC2Client}
import com.amazonaws.services.elasticloadbalancing.{AmazonElasticLoadBalancingAsyncClient, AmazonElasticLoadBalancingClient}
import com.amazonaws.services.elasticloadbalancing.model.{DescribeLoadBalancersRequest, DescribeLoadBalancersResult}

import scala.concurrent.{Future, Promise}


object AWS {
  def credentialsProviderChain = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider(),
    new ProfileCredentialsProvider("security")
  )

  // EC2

  def ec2client(region: Region): AmazonEC2Client = {
    val ec2Client = new AmazonEC2Client(credentialsProviderChain)
    ec2Client.setRegion(region)
    ec2Client
  }

  // ELBs

  def elbClient(region: Region) = {
    val elbClient = new AmazonElasticLoadBalancingClient(credentialsProviderChain)
    elbClient.setRegion(region)
    elbClient
  }

  def describeLoadBalancers(elbClient: AmazonElasticLoadBalancingClient): DescribeLoadBalancersResult = {
    val request = new DescribeLoadBalancersRequest()
    elbClient.describeLoadBalancers(request)
  }
}
