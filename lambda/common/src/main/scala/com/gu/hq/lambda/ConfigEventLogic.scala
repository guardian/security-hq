package com.gu.hq.lambda

import com.amazonaws.services.lambda.runtime.events.ConfigEvent
import com.gu.hq.lambda.model.{InvokingEvent, JSON, SGConfiguration}
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.libs.json.{JsValue, Json, Reads}


object ConfigEventLogic {
  import JSON._

  def eventDetails(event: ConfigEvent): Option[InvokingEvent] = {
    extract[InvokingEvent](Json.parse(event.getInvokingEvent))
  }

  def sgConfiguration(configurationJson: JsValue): Option[SGConfiguration] = {
    extract[SGConfiguration](configurationJson)
  }

  private def extract[A](json: JsValue)(implicit rds: Reads[A]): Option[A] = {
    json.validate[A].fold(
      { errs =>
        println(errs)
        None
      },
      { Some(_) }
    )
  }
}
