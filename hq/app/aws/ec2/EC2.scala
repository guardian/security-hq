package aws.ec2

import aws.AWS
import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.support.{TrustedAdvisor, TrustedAdvisorSGOpenPorts}
import cats.instances.map._
import cats.instances.set._
import cats.syntax.semigroup._
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.model._
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import model._
import utils.attempt.{Attempt, FailedAttempt}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object EC2 {
  def client(auth: AWSCredentialsProviderChain, region: Regions): AmazonEC2Async = {
    AmazonEC2AsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(region)
      .build()
  }

  def client(awsAccount: AwsAccount, region: Regions): AmazonEC2Async = {
    val auth = AWS.credentialsProvider(awsAccount)
    client(auth, region)
  }

  /**
    * Given a Trusted Advisor Security Group open ports result,
    * makes EC2 calls in each region to look up the Network Interfaces
    * attached to each flagged Security Group.
    */
  def getSgsUsage(sgReport: TrustedAdvisorDetailsResult[SGOpenPortsDetail], awsAccount: AwsAccount)(implicit ec: ExecutionContext): Attempt[Map[String, Set[SGInUse]]] = {
    val allSgIds = TrustedAdvisorSGOpenPorts.sgIds(sgReport)
    val activeRegions = sgReport.flaggedResources.map(sgInfo => Regions.fromName(sgInfo.region)).distinct

    for {
      dnirs <- Attempt.traverse(activeRegions){ region =>
        getSgsUsageForRegion(allSgIds, client(awsAccount, region))
      }
    } yield {
      dnirs
        .map(parseDescribeNetworkInterfacesResults(_, allSgIds))
        .fold(Map.empty)(_ |+| _)
    }
  }

  def flaggedSgsForAccount(account: AwsAccount)(implicit ec: ExecutionContext): Attempt[List[(SGOpenPortsDetail, Set[SGInUse])]] = {
    val supportClient = TrustedAdvisor.client(account)
    for {
      sgResult <- TrustedAdvisorSGOpenPorts.getSGOpenPorts(supportClient)
      sgUsage <- getSgsUsage(sgResult, account)
      flaggedSgs <- updateSgsWithTags(account, sgResult.flaggedResources.filter(_.status != "ok")){ (ec2Client, request) =>
        handleAWSErrs(awsToScala(ec2Client.describeSecurityGroupsAsync)(request))
      }
      vpcs <- getVpcs(account, flaggedSgs)(getVpcsDetails)
      flaggedSgsWithVpc = addVpcName(flaggedSgs, vpcs)
    } yield sortSecurityGroupsByInUse(flaggedSgsWithVpc, sgUsage)
  }

  def allFlaggedSgs(accounts: List[AwsAccount])(implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        flaggedSgsForAccount(account).asFuture.map(account -> _)
      }
    }
  }

  private [ec2] def sortSecurityGroupsByInUse(sgsPortFlags : List[SGOpenPortsDetail], sgUsage: Map[String, Set[SGInUse]]) = {
    sgsPortFlags
      .map(sgOpenPortsDetail => sgOpenPortsDetail -> sgUsage.getOrElse(sgOpenPortsDetail.id, Set.empty))
      .sortWith{ case  ((_, s1), (_, s2)) => s1.size > s2.size }
  }

  def sortAccountByFlaggedSgs[L, R](accountsWithFlaggedSgs: List[(AwsAccount, Either[L, List[R]])]): List[(AwsAccount, Either[L, List[R]])] = {
    accountsWithFlaggedSgs.sortBy {
      // first, non-empty flagged results list
      // sort internally by number of flagged results, decreasing (largest number of flagged results first)
      case (_, Right(flaggedSgs)) if flaggedSgs.nonEmpty =>
        (0, 0, flaggedSgs.length * -1, "")
      // second, failed to get results
      // sort internally by name of account
      case (account, Left(_)) =>
        (0, 1, 0, account.name)
      // finally, empty flagged results
      // sort internally by name of account
      case (account, Right(flaggedSgs)) =>
        (1, 0, 0, account.name)
    }
  }

  private[ec2] def getSgsUsageForRegion(sgIds: List[String], client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[DescribeNetworkInterfacesResult] = {
    val request = new DescribeNetworkInterfacesRequest()
        .withFilters(new Filter("group-id", sgIds.asJava))
    handleAWSErrs(awsToScala(client.describeNetworkInterfacesAsync)(request))
  }

  private[ec2] def parseDescribeNetworkInterfacesResults(dnir: DescribeNetworkInterfacesResult, sgIds: List[String]): Map[String, Set[SGInUse]] = {
    dnir.getNetworkInterfaces.asScala.toSet
      .flatMap { (ni: NetworkInterface) =>
        val sgUse = parseNetworkInterface(ni)
        val matchingGroupIds = ni.getGroups.asScala.toList.filter(gi => sgIds.contains(gi.getGroupId))
        matchingGroupIds.map(_ -> sgUse)
      }
      .groupBy { case (sgId, _) => sgId.getGroupId }
      .mapValues { pairs =>
        pairs.map { case (_, sgUse) => sgUse }
      }
  }

  private[ec2] def parseNetworkInterface(ni: NetworkInterface): SGInUse = {
    val elb = Option(ni.getAttachment.getInstanceOwnerId).find(_ == "amazon-elb")
      .map(_ => ELB(ni.getDescription.stripPrefix("ELB ")))
    val instance = Option(ni.getAttachment.getInstanceId).map(Ec2Instance)

    elb
      .orElse(instance)
      .getOrElse(
        UnknownUsage(ni.getDescription, ni.getNetworkInterfaceId)
      )
  }

  def updateSgsWithTags(account: AwsAccount, sgIds: List[SGOpenPortsDetail])
                       (sgsDetailsFn: (AmazonEC2Async, DescribeSecurityGroupsRequest) => Attempt[DescribeSecurityGroupsResult])
                       (implicit ec: ExecutionContext): Attempt[List[SGOpenPortsDetail]] = {

    val regions = sgIds.map(_.region).distinct

    Attempt.flatTraverse(regions) { region =>
      val sgsFiltered = sgIds.filter(_.region == region)
      val ec2Client = client(account, Regions.fromName(region))
      val request = new DescribeSecurityGroupsRequest().withGroupIds(sgsFiltered.map(_.id).asJava)
      sgsDetailsFn(ec2Client, request) map updateSGOpenPorts(sgsFiltered)
    }
  }

  private def updateSGOpenPorts(sgsOpenPorts: List[SGOpenPortsDetail])(result: DescribeSecurityGroupsResult) = {
    val cfStack = "aws:cloudformation:stack-name"
    val cfStackId = "aws:cloudformation:stack-id"

    def getTagValue(sgWithTags: Map[String, Seq[Tag]], sg: SGOpenPortsDetail, tagId: String) = {
      sgWithTags.collectFirst {case (id, tags) if id == sg.id  => tags.find(_.getKey == tagId).map(_.getValue) }.flatten
    }

    val sgWithTags = result.getSecurityGroups.asScala.foldLeft(Map.empty[String, Seq[Tag]]) { case (m, sg) =>
      m + ( sg.getGroupId -> sg.getTags.asScala)
    }

    sgsOpenPorts.map(sg => sg.copy(stackName = getTagValue(sgWithTags, sg, cfStack), stackId =  getTagValue(sgWithTags, sg, cfStackId) ))
  }


  private[ec2] def addVpcName(flaggedSgs: List[SGOpenPortsDetail], vpcs: Map[String, Vpc]): List[SGOpenPortsDetail] = {
    def vpcName(vpc: Vpc) = vpc.getTags.asScala.collectFirst { case tag if tag.getKey == "Name" => tag.getValue }

    flaggedSgs.map {
      case s if s.vpcId.nonEmpty => s.copy(vpcName = vpcs.get(s.vpcId) flatMap vpcName)
      case s => s
    }
  }

  private[ec2] def getVpcs(account: AwsAccount, flaggedSgs: List[SGOpenPortsDetail])(vpcsDetailsF: AmazonEC2Async => Attempt[Map[String, Vpc]])(implicit ec: ExecutionContext): Attempt[Map[String, Vpc]] = {
    Attempt.traverse(flaggedSgs.map(_.region).distinct) { region =>
      val ec2Client = client(account, Regions.fromName(region))
      vpcsDetailsF(ec2Client)
    }.map(_.fold(Map.empty)(_ ++ _))
  }

  private def getVpcsDetails(client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[Map[String, Vpc]] = {
    val request = new DescribeVpcsRequest()
    val vpcsResult = handleAWSErrs(awsToScala(client.describeVpcsAsync)(request))
    vpcsResult.map { result =>
      result.getVpcs.asScala.map(vpc => vpc.getVpcId -> vpc).toMap
    }
  }
}
