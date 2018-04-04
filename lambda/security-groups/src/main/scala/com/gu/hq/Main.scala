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
    val ec2Client = AWS.ec2client(region)
    val elbClient = AWS.elbClient(region)
    val snsClient = AWS.snsClient(region)
    val sgConfiguration: SGConfiguration = new SGConfiguration(
      "test",
      "test",
      args(2),
      "test",
      args(3).split(",").toList.map(s => IpPermission("test", None, None, List(), List(s), List())),
      List(),
      "test",
      args(4).split(",").toList.map(s => {val kv = s.split("="); new Tag(key=kv(0), value=kv(1))})
    )
    val notify = args(5).equals("true")
    val snsTopicArn = args(6)

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
            args(1),
            sgConfiguration.tags,
            args(2),
            snsTopicArn,
            snsClient)
    }
  }

}
