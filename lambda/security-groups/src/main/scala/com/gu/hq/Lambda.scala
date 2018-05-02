package com.gu.hq

import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.runtime.events.ConfigEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.hq.lambda.JsonParsing
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
      invokingEvent <- JsonParsing.eventDetails(input)
      configurationItem <- invokingEvent.configurationItem
      regionName <- configurationItem.awsRegion
      sgConfiguration <- JsonParsing.sgConfiguration(configurationItem.configuration)
      loadBalancers = AWS.describeLoadBalancers(elbClient)
      account = AWS.accountNumber(stsClient)
      accountMapping = JsonParsing.accountMapping(AWS.accountsMappingJson(s3Client)) getOrElse Map()
      accountName = accountMapping.getOrElse(account, s"Unidentified account ($account)")
      status = SecurityGroups.status(sgConfiguration, loadBalancers)
      relevance = Events.relevance(invokingEvent)
      _ <- Notifier.shouldNotify(relevance, status)
      notification = Notifier.createNotification(sgConfiguration.groupId, sgConfiguration.tags, account, accountName, regionName)
    } Notifier.send(notification, snsTopicArn, snsClient)
  }
}
