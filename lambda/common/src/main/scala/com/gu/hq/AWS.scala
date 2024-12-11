package com.gu.hq

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest

import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.{DescribeLoadBalancersRequest, DescribeLoadBalancersResponse}
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest

object AWS {
  def deployToolsCredentialsProviderChain = AwsCredentialsProviderChain.of(
    DefaultCredentialsProvider.create(),
    ProfileCredentialsProvider.create("deployTools")
  )

  def securityCredentialsProviderChain = AwsCredentialsProviderChain.of(
    DefaultCredentialsProvider.create(),
    ProfileCredentialsProvider.create("security")
  )

  def developerPlaygroundCredentialsProviderChain = AwsCredentialsProviderChain.of(
    DefaultCredentialsProvider.create(),
    ProfileCredentialsProvider.create("developerPlayground")
  )

  // ELBs
  def elbClient(region: Region): ElasticLoadBalancingV2Client = {
    ElasticLoadBalancingV2Client.builder.region(region).credentialsProvider(securityCredentialsProviderChain).build()
  }

  // SNSs
  def snsClient(region: Region): SnsAsyncClient = {
    SnsAsyncClient.builder.region(region).credentialsProvider(securityCredentialsProviderChain).build()
  }
  // STS
  def stsClient(region: Region): StsClient = {
    StsClient.builder.region(region).credentialsProvider(securityCredentialsProviderChain).build()
  }

  // S3
  def s3Client(region: Region): S3Client = {
    S3Client.builder.region(region).credentialsProvider(securityCredentialsProviderChain).build()
  }

  def describeLoadBalancers(elbClient: ElasticLoadBalancingV2Client): DescribeLoadBalancersResponse = {
    val request = DescribeLoadBalancersRequest.builder.build()
    elbClient.describeLoadBalancers(request)
  }

  def accountNumber(stsClient: StsClient): String = {
    stsClient.getCallerIdentity(GetCallerIdentityRequest.builder.build()).account
  }

  def accountsMappingJson(s3Client: S3Client): String = {
    val request = GetObjectRequest.builder.bucket("guardian-dist").key("guardian/PROD/accounts").build()
    scala.io.Source.fromInputStream(s3Client.getObject(request)).mkString
  }

}
