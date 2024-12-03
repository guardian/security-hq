package com.gu.hq

import com.gu.hq.lambda.model.SGConfiguration
import com.typesafe.scalalogging.StrictLogging
import software.amazon.awssdk.services.elasticloadbalancingv2.model.{DescribeLoadBalancersRequest, DescribeLoadBalancersResponse}

import scala.jdk.CollectionConverters._


object SecurityGroups extends StrictLogging{
  private[hq] def openToWorld(sGConfiguration: SGConfiguration): Boolean = {
    sGConfiguration.ipPermissions.exists(_.ipRanges.contains("0.0.0.0/0"))
  }

  private[hq] def attachedToElb(sgConfiguration: SGConfiguration, loadBalancers: DescribeLoadBalancersResponse): Boolean = {
    val elbDescriptions = loadBalancers.loadBalancers.asScala.toSet
    val elbSGs = elbDescriptions.flatMap(_.securityGroups.asScala)
    elbSGs.contains(sgConfiguration.groupId)
  }

  def status(sgConfiguration: SGConfiguration, loadBalancers: DescribeLoadBalancersResponse): SgStatus = {
    val open = openToWorld(sgConfiguration)
    val elbSg = attachedToElb(sgConfiguration, loadBalancers)

    if (open && elbSg) {
      logger.warn(s"Open to world and attached to ELB: ${sgConfiguration.groupName}")
      OpenELB
    }
    else if (open) {
      logger.warn(s"Open to world: ${sgConfiguration.groupName}")
      Open
    }
    else {
      logger.info(s"Not open to world: ${sgConfiguration.groupName}")
      NotOpen
    }
  }

  sealed trait SgStatus
  case object NotOpen extends SgStatus
  case object OpenELB extends SgStatus
  case object Open extends SgStatus
}
