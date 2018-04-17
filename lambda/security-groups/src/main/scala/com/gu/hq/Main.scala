package com.gu.hq

import com.amazonaws.regions.Regions
import com.gu.hq.SecurityGroups.{NotOpen, Open, OpenELB}
import com.gu.hq.lambda.model._
import com.typesafe.scalalogging.StrictLogging

object Main extends StrictLogging {

  def main(args: Array[String]): Unit = {

    if (args.length!=7) {
      logger.error("Need precisely seven arguments: <region> <account name> <security group id> <cidr,cidr...> <tag,tag...> <boolean (notify)> <sns topic arn>")
      System.exit(1)
    }

    val region = Regions.fromName(args(0))
    val accountNumber = args(1)
    val sgId = args(2)
    val cidrs = args(3)
    val tags = args(4)
    val notify = args(5).equals("true")
    val snsTopicArn = args(6)

    val elbClient = AWS.elbClient(region)
    val snsClient = AWS.snsClient(region)
    val s3Client = AWS.s3Client(region)

    val sgConfiguration: SGConfiguration = SGConfiguration(
      "test",
      "test",
      sgId,
      "test",
      cidrs.split(",").toList.map(s => IpPermission("test", None, None, List(), List(s), List())),
      List(),
      "test",
      tags.split(",").toList.map(s => {val kv = s.split("="); Tag(key=kv(0), value=kv(1))})
    )

    val loadBalancers = AWS.describeLoadBalancers(elbClient)

    SecurityGroups.status(sgConfiguration, loadBalancers) match {
      case NotOpen =>
        logger.debug(s"${sgConfiguration.groupId}: Security group")
      case OpenELB =>
        logger.debug(s"${sgConfiguration.groupId}: Open Security Group attached to ELB")
      case Open =>
        // persist/alert a warning of some sort
        logger.warn(s"${sgConfiguration.groupId}: Open security group")
        if (notify)
          Notifier.send(
            sgConfiguration.groupId,
            sgConfiguration.tags,
            accountNumber,
            region.getName,
            snsTopicArn,
            s3Client,
            snsClient)
    }
  }

}
