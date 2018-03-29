package com.gu.hq

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.runtime.events.ConfigEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.hq.SecurityGroups.{NotOpen, Open, OpenELB}
import com.gu.hq.lambda.ConfigEventLogic
import com.typesafe.scalalogging.StrictLogging


class Lambda extends RequestHandler[ConfigEvent, Unit] with StrictLogging {
  val region = Regions.fromName(System.getenv("AWS_DEFAULT_REGION"))
  val ec2Client = AWS.ec2client(region)
  val elbClient = AWS.elbClient(region)

  override def handleRequest(input: ConfigEvent, context: Context): Unit = {
    logger.debug(s"Starting check of ${input}")
    for {
      invokingEvent <- ConfigEventLogic.eventDetails(input)
      configurationItem <- invokingEvent.configurationItem
      sgConfiguration <- ConfigEventLogic.sgConfiguration(configurationItem.configuration)
      loadBalancers = AWS.describeLoadBalancers(elbClient)
    } SecurityGroups.status(sgConfiguration, loadBalancers) match {
      case NotOpen =>
        logger.debug(s"${sgConfiguration.groupId}: Security group")
      case OpenELB =>
        logger.debug(s"${sgConfiguration.groupId}: Open Security Group attached to ELB")
      case Open =>
        // persist/alert a warning of some sort
        logger.warn(s"${sgConfiguration.groupId}: Open security group")
    }
  }
}
