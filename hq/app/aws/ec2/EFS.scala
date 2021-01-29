package aws.ec2

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.AwsClient
import com.amazonaws.services.elasticfilesystem.AmazonElasticFileSystemAsync
import com.amazonaws.services.elasticfilesystem.model._
import model.{SGInUse, UnknownUsage}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

/**
  * Given an Elastic File System (EFS), finds the security groups of its Mount Targets
  * and filters for those that are flagged by Trusted Advisor.
  */
object EFS {
  def getFileSystems(client: AwsClient[AmazonElasticFileSystemAsync])(implicit ec: ExecutionContext): Attempt[DescribeFileSystemsResult] = {
    handleAWSErrs(client)(awsToScala(client)(_.describeFileSystemsAsync)(new DescribeFileSystemsRequest()))
  }

  def getMountTargets(efsInstances: DescribeFileSystemsResult, client: AwsClient[AmazonElasticFileSystemAsync])(implicit ec: ExecutionContext): Attempt[List[DescribeMountTargetsResult]] = {
     Attempt.traverse(efsInstances.getFileSystems.asScala.toList){ fileSystem =>
      val request = new DescribeMountTargetsRequest().withFileSystemId(fileSystem.getFileSystemId)
      handleAWSErrs(client)(awsToScala(client)(_.describeMountTargetsAsync)(request))
    }
  }

  def getMountTargetSecGrps(mountTargets: List[DescribeMountTargetsResult], client: AwsClient[AmazonElasticFileSystemAsync])(implicit ec: ExecutionContext): Attempt[List[(String, DescribeMountTargetSecurityGroupsResult)]] = {
    val mts = mountTargets.flatMap(_.getMountTargets.asScala.toList)
    Attempt.traverse(mts){ mt =>
      val mtId = mt.getMountTargetId
      val fileSystemId = mt.getFileSystemId
      val request = new DescribeMountTargetSecurityGroupsRequest().withMountTargetId(mtId)
      val secGrps = handleAWSErrs(client)(awsToScala(client)(_.describeMountTargetSecurityGroupsAsync)(request))
      secGrps.map(fileSystemId -> _)
    }
  }

  def secGrpToKey(fileSystemIdToSecGrp: List[(String, DescribeMountTargetSecurityGroupsResult)]): List[(String, SGInUse)] = {
    val secGrpToFileSystemId = fileSystemIdToSecGrp.flatMap { case (efs, secGrps) =>
        secGrps.getSecurityGroups.asScala.toList.map { scGrp =>
          scGrp -> model.EfsVolume(efs)
        }
    }
    secGrpToFileSystemId
  }

  def getFlaggedSecGrps(sgIds: List[String], efsSecGrps: List[(String, SGInUse)]):List[(String, SGInUse)] = {
    efsSecGrps.filter{ case (scGrp, _) =>
      sgIds.contains(scGrp)
    }
  }

  // these functions remove the UnknownUsage EFS values, because we already have the correct EfsVolume value
  def removeAllEfsUnknownUsages(allSecGrps: Attempt[Map[String, Set[SGInUse]]])(implicit ec: ExecutionContext): Attempt[Map[String, Set[SGInUse]]] = allSecGrps.map(filterUnknownEfsSecGrps)
  def filterUnknownEfsSecGrps(secGrpToResources: Map[String, Set[SGInUse]]): Map[String, Set[SGInUse]] = secGrpToResources.mapValues(filterResources)
  def filterResources(resources: Set[SGInUse]): Set[SGInUse] = resources.filter(filterResource)
  def filterResource(resource: SGInUse): Boolean = resource match {
    case resource:UnknownUsage => if (resource.description.contains("EFS mount target")) false else true
    case _ => true
  }
}

