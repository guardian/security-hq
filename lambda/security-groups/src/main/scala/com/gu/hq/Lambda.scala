package com.gu.hq

import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.runtime.events.ConfigEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.hq.SecurityGroups.{NotOpen, Open, OpenELB}
import com.gu.hq.lambda.ConfigEventLogic
import com.typesafe.scalalogging.StrictLogging

class Lambda extends RequestHandler[ConfigEvent, Unit] with StrictLogging {
  private val region = Regions.fromName(System.getenv("AWS_DEFAULT_REGION"))
  private val elbClient = AWS.elbClient(region)
  private val snsClient = AWS.snsClient(region)
  private val stsClient = AWS.stsClient(region)
  private val s3Client = AWS.s3Client(region)
  private val snsTopicArn = sys.env("SnsTopicArn")

  override def handleRequest(input: ConfigEvent, context: Context): Unit = {
    logger.debug(s"Starting check of $input")
    for {
      invokingEvent <- ConfigEventLogic.eventDetails(input)
      configurationItem <- invokingEvent.configurationItem
      regionName <- configurationItem.awsRegion
      sgConfiguration <- ConfigEventLogic.sgConfiguration(configurationItem.configuration)
      loadBalancers = AWS.describeLoadBalancers(elbClient)
      account = AWS.accountNumber(stsClient)
    } SecurityGroups.status(sgConfiguration, loadBalancers) match {
      case NotOpen =>
        logger.debug(s"${sgConfiguration.groupId}: Security group")
      case OpenELB =>
        logger.debug(s"${sgConfiguration.groupId}: Open Security Group attached to ELB")
      case Open =>
        logger.info(s"${sgConfiguration.groupId}: Open security group")
        Notifier.send(
          sgConfiguration.groupId,
          sgConfiguration.tags,
          account,
          regionName,
          snsTopicArn,
          s3Client,
          snsClient
        )
    }
  }
}
