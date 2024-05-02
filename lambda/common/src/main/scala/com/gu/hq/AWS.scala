package com.gu.hq

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Region
import com.amazonaws.services.elasticloadbalancing.model.{DescribeLoadBalancersRequest, DescribeLoadBalancersResult}
import com.amazonaws.services.elasticloadbalancing.{AmazonElasticLoadBalancingAsync, AmazonElasticLoadBalancingAsyncClient}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.{AWSSecurityTokenServiceAsync, AWSSecurityTokenServiceAsyncClient}
import com.amazonaws.services.sns.{AmazonSNSAsync, AmazonSNSAsyncClient}


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

  // ELBs
  def elbClient(region: Region): AmazonElasticLoadBalancingAsync = {
    AmazonElasticLoadBalancingAsyncClient.asyncBuilder().withRegion(region.getName).withCredentials(securityCredentialsProviderChain).build()
  }

  // SNSs
  def snsClient(region: Region): AmazonSNSAsync = {
    AmazonSNSAsyncClient.asyncBuilder().withRegion(region.getName).withCredentials(securityCredentialsProviderChain ).build()
  }
  // STS
  def stsClient(region: Region): AWSSecurityTokenServiceAsync = {
    AWSSecurityTokenServiceAsyncClient.asyncBuilder().withRegion(region.getName).withCredentials(securityCredentialsProviderChain ).build()
  }

  // S3
  def s3Client(region: Region): AmazonS3 = {
    AmazonS3ClientBuilder.standard().withRegion(region.getName).withCredentials(securityCredentialsProviderChain).build()
  }

  def describeLoadBalancers(elbClient: AmazonElasticLoadBalancingAsync): DescribeLoadBalancersResult = {
    val request = new DescribeLoadBalancersRequest()
    elbClient.describeLoadBalancers(request)
  }

  def accountNumber(stsClient: AWSSecurityTokenServiceAsync): String = {
    stsClient.getCallerIdentity(new GetCallerIdentityRequest()).getAccount
  }

  def accountsMappingJson(s3Client: AmazonS3): String = {
    scala.io.Source.fromInputStream(s3Client.getObject("guardian-dist", "guardian/PROD/accounts").getObjectContent).mkString
  }

}
