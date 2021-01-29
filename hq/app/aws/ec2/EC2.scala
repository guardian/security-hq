package aws.ec2

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.ec2.EFS.removeAllEfsUnknownUsages
import aws.support.TrustedAdvisorSGOpenPorts
import aws.{AwsClient, AwsClients}
import cats.instances.map._
import cats.instances.set._
import cats.syntax.semigroup._
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._
import com.amazonaws.services.elasticfilesystem.AmazonElasticFileSystemAsync
import com.amazonaws.services.support.AWSSupportAsync
import com.amazonaws.services.support.model.RefreshTrustedAdvisorCheckResult
import model._
import utils.attempt.{Attempt, FailedAttempt}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object EC2 {

  def getAvailableRegions(client: AwsClient[AmazonEC2Async])(implicit ec: ExecutionContext): Attempt[List[Region]] = {
    val request = new DescribeRegionsRequest()
    handleAWSErrs(client)(awsToScala(client)(_.describeRegionsAsync)(request)).map { result =>
      result.getRegions.asScala.toList
    }
  }

  /**
    * Given a Trusted Advisor Security Group open ports result,
    * makes EC2 calls in each region to look up the Network Interfaces
    * attached to each flagged Security Group.
    */
  def getSgsUsage(
      sgReport: TrustedAdvisorDetailsResult[SGOpenPortsDetail],
      awsAccount: AwsAccount,
      ec2Clients: AwsClients[AmazonEC2Async],
      efsClients: AwsClients[AmazonElasticFileSystemAsync]
    )(implicit ec: ExecutionContext): Attempt[Map[String, Set[SGInUse]]] = {
    val allSgIds = TrustedAdvisorSGOpenPorts.sgIds(sgReport)
    val activeRegions = sgReport.flaggedResources.map(sgInfo => Regions.fromName(sgInfo.region)).distinct

    val efsSgs: Attempt[Map[String, Set[SGInUse]]] = for {
      efsSecGrps <- Attempt.traverse(activeRegions){ region =>
        val clients = efsClients.get(awsAccount, region)
        clients.flatMap { client =>
          val efsInstances = EFS.getFileSystems(client)
          val mountTargets = efsInstances.flatMap(EFS.getMountTargets(_, client))
          val mountTargetSecGrps = mountTargets.flatMap(EFS.getMountTargetSecGrps(_, client))
          val allEfsSecGrps = mountTargetSecGrps.map(EFS.secGrpToKey)
          val flaggedEfsSecGrps = allEfsSecGrps.map(EFS.getFlaggedSecGrps(allSgIds, _))
          flaggedEfsSecGrps
        }
      }
    } yield efsSecGrps.flatten.groupBy(_._1).mapValues(_.map(_._2).toSet)

    val ec2Sgs: Attempt[Map[String, Set[SGInUse]]] = for {

      dnirs <- Attempt.traverse(activeRegions){ region =>
        for {
          ec2Client <- ec2Clients.get(awsAccount, region)
          usage <- getSgsUsageForRegion(allSgIds, ec2Client)
        } yield usage

      }
    } yield {
      dnirs
        .map(parseDescribeNetworkInterfacesResults(_, allSgIds))
        .fold(Map.empty)(_ |+| _)
    }

    val ec2AndEfsSecGrps = for {
      efsSg <- efsSgs
      ec2Sg <- ec2Sgs
    } yield List(ec2Sg, efsSg).fold(Map.empty)(_ |+| _)

    removeAllEfsUnknownUsages(ec2AndEfsSecGrps)
  }

  def flaggedSgsForAccount(account: AwsAccount, ec2Clients: AwsClients[AmazonEC2Async], efsClients: AwsClients[AmazonElasticFileSystemAsync], taClients: AwsClients[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[(SGOpenPortsDetail, Set[SGInUse])]] = {
    for {
      supportClient <- taClients.get(account)
      sgResult <- TrustedAdvisorSGOpenPorts.getSGOpenPorts(supportClient)
      sgUsage <- getSgsUsage(sgResult, account, ec2Clients, efsClients)
      flaggedSgs = sgResult.flaggedResources.filter(_.status != "ok")
      flaggedSgsIds = flaggedSgs.map(_.id)
      regions = flaggedSgs.map(sg => Regions.fromName(sg.region)).distinct
      clients <- Attempt.traverse(regions)(region => ec2Clients.get(account, region))
      describeSecurityGroupsResults <- Attempt.traverse(clients)(EC2.describeSecurityGroups(flaggedSgsIds))
      sgTagDetails = describeSecurityGroupsResults.flatMap(extractTagsForSecurityGroups).toMap
      enrichedFlaggedSgs = enrichSecurityGroups(flaggedSgs, sgTagDetails)
      vpcs <- getVpcs(account, enrichedFlaggedSgs, ec2Clients)(getVpcsDetails)
      flaggedSgsWithVpc = addVpcName(enrichedFlaggedSgs, vpcs)
    } yield sortSecurityGroupsByInUse(flaggedSgsWithVpc, sgUsage)
  }

  def refreshSGSReports(accounts: List[AwsAccount], taClients: AwsClients[AWSSupportAsync])(implicit ec: ExecutionContext): Attempt[List[Either[FailedAttempt, RefreshTrustedAdvisorCheckResult]]] = {
    Attempt.traverseWithFailures(accounts) { account =>
      for {
        supportClient <- taClients.get(account)
        result <- TrustedAdvisorSGOpenPorts.refreshSGOpenPorts(supportClient)
      } yield result
    }
  }

  def allFlaggedSgs(accounts: List[AwsAccount], ec2Clients: AwsClients[AmazonEC2Async], efsClients: AwsClients[AmazonElasticFileSystemAsync], taClients: AwsClients[AWSSupportAsync])
  (implicit ec: ExecutionContext): Attempt[List[(AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]])]] = {
    Attempt.Async.Right {
      Future.traverse(accounts) { account =>
        flaggedSgsForAccount(account, ec2Clients, efsClients, taClients).asFuture.map(account -> _)
      }
    }
  }

  private [ec2] def sortSecurityGroupsByInUse(sgsPortFlags : List[SGOpenPortsDetail], sgUsage: Map[String, Set[SGInUse]]) = {
    sgsPortFlags
      .map(sgOpenPortsDetail => sgOpenPortsDetail -> sgUsage.getOrElse(sgOpenPortsDetail.id, Set.empty))
      .sortWith { case  ((_, s1), (_, s2)) => s1.size > s2.size }
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
      case (account, Right(_)) =>
        (1, 0, 0, account.name)
    }
  }

  private def describeSecurityGroups(sgIds: List[String])(client: AwsClient[AmazonEC2Async])(implicit ec: ExecutionContext): Attempt[DescribeSecurityGroupsResult] = {
    val request = new DescribeSecurityGroupsRequest()
      .withFilters(new Filter("group-id", sgIds.asJava))
    handleAWSErrs(client)(awsToScala(client)(_.describeSecurityGroupsAsync)(request))
  }

  private[ec2] def extractTagsForSecurityGroups(describeSecurityGroupsResult: DescribeSecurityGroupsResult): Map[String, List[Tag]] = {
    val sgs = describeSecurityGroupsResult.getSecurityGroups.asScala.toList
    sgs.map { sg =>
      sg.getGroupId -> sg.getTags.asScala.toList
    }.toMap
  }

  private[ec2] def enrichSecurityGroups(sGOpenPortsDetails: List[SGOpenPortsDetail], sgTagDetails: Map[String, List[Tag]]): List[SGOpenPortsDetail] = {
    sGOpenPortsDetails.map { sGOpenPortsDetail =>
      val enrichedSGOpenPortsDetail = for {
        tags <- sgTagDetails.get(sGOpenPortsDetail.id)
        cfStackNameTag <- tags.find(_.getKey == "aws:cloudformation:stack-name")
        cfStackIdTag <- tags.find(_.getKey == "aws:cloudformation:stack-id")
      } yield sGOpenPortsDetail.copy(stackName = Some(cfStackNameTag.getValue), stackId = Some(cfStackIdTag.getValue))
      enrichedSGOpenPortsDetail.getOrElse(sGOpenPortsDetail)
    }
  }

  private[ec2] def getSgsUsageForRegion(sgIds: List[String], client: AwsClient[AmazonEC2Async])(implicit ec: ExecutionContext): Attempt[DescribeNetworkInterfacesResult] = {
    val request = new DescribeNetworkInterfacesRequest()
        .withFilters(new Filter("group-id", sgIds.asJava))
    handleAWSErrs(client)(awsToScala(client)(_.describeNetworkInterfacesAsync)(request))
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
    val elb = for {
      networkInterfaceAttachment  <- Option(ni.getAttachment)
      instanceOwnerID <- Option(networkInterfaceAttachment.getInstanceOwnerId)
      if instanceOwnerID == "amazon-elb"
    } yield ELB(ni.getDescription.stripPrefix("ELB "))

    val instance = for {
      networkInterfaceAttachment <- Option(ni.getAttachment)
      instanceID <- Option(networkInterfaceAttachment.getInstanceId)
    } yield Ec2Instance(instanceID)

    elb
      .orElse(instance)
      .getOrElse(
        UnknownUsage(
          Option(ni.getDescription).getOrElse("No network interface description"),
          Option(ni.getNetworkInterfaceId).getOrElse("No network interface ID")
        )
      )
  }

  private[ec2] def addVpcName(flaggedSgs: List[SGOpenPortsDetail], vpcs: Map[String, Vpc]): List[SGOpenPortsDetail] = {
    def vpcName(vpc: Vpc) = vpc.getTags.asScala.collectFirst { case tag if tag.getKey == "Name" => tag.getValue }

    flaggedSgs.map {
      case s if s.vpcId.nonEmpty => s.copy(vpcName = vpcs.get(s.vpcId) flatMap vpcName)
      case s => s
    }
  }

  private[ec2] def getVpcs(account: AwsAccount, flaggedSgs: List[SGOpenPortsDetail], ec2Clients: AwsClients[AmazonEC2Async])(vpcsDetailsF: AwsClient[AmazonEC2Async] => Attempt[Map[String, Vpc]])(implicit ec: ExecutionContext): Attempt[Map[String, Vpc]] = {
    Attempt.traverse(flaggedSgs.map(_.region).distinct) { region =>
      val awsRegion = Regions.fromName(region)
      for {
        ec2Client <- ec2Clients.get(account, awsRegion)
        vpcDetails <- vpcsDetailsF(ec2Client)
      } yield vpcDetails

    }.map(_.fold(Map.empty)(_ ++ _))
  }

  private def getVpcsDetails(client: AwsClient[AmazonEC2Async])(implicit ec: ExecutionContext): Attempt[Map[String, Vpc]] = {
    val request = new DescribeVpcsRequest()
    val vpcsResult = handleAWSErrs(client)(awsToScala(client)(_.describeVpcsAsync)(request))
    vpcsResult.map { result =>
      result.getVpcs.asScala.map(vpc => vpc.getVpcId -> vpc).toMap
    }
  }
}
