package com.gu.hq

import com.amazonaws.services.lambda.runtime.events.ConfigEvent

import scala.io.Source

object Main extends App{

  private val event = new ConfigEvent()
  private val source = Source.fromFile("/Users/natasha_thrale/code/security-hq/lambda/common/src/test/resources/config_event_with_relevant_update.json")
  private val jsonString = source.getLines().mkString
  source.close()
  event.setInvokingEvent(jsonString)

  val lambda= new Lambda()
  lambda.doTheThing(event)
}
