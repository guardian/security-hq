package com.gu.hq

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.gu.hq.lambda.model.SGConfiguration

import scala.collection.JavaConverters._


object SecurityGroups {
  private[hq] def openToWorld(sGConfiguration: SGConfiguration): Boolean = {
    sGConfiguration.ipPermissions.exists(_.ipRanges.contains("0.0.0.0/0"))
  }

  private[hq] def attachedToElb(sgConfiguration: SGConfiguration, loadBalancers: DescribeLoadBalancersResult): Boolean = {
    val elbDescriptions = loadBalancers.getLoadBalancerDescriptions.asScala.toSet
    val elbSGs = elbDescriptions.flatMap(_.getSecurityGroups.asScala)
    elbSGs.contains(sgConfiguration.groupId)
  }

  private[hq] def isExactlyHttps(sGConfiguration: SGConfiguration): Boolean = {
    sGConfiguration.ipPermissions.exists(ipPermission => {
      ipPermission.fromPort.contains(443) && ipPermission.toPort.contains(443)
    })
  }

  def status(sgConfiguration: SGConfiguration, loadBalancers: DescribeLoadBalancersResult): SgStatus = {
    val open = openToWorld(sgConfiguration)
    val elbSg = attachedToElb(sgConfiguration, loadBalancers)
    val exactlyHttps = isExactlyHttps(sgConfiguration)

    if (open && elbSg && !exactlyHttps) OpenELB
    else if (open && !exactlyHttps) Open
    else NotOpen
  }

  sealed trait SgStatus
  case object NotOpen extends SgStatus
  case object OpenELB extends SgStatus
  case object Open extends SgStatus
}
