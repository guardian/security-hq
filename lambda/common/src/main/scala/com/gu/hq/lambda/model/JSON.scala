package com.gu.hq.lambda.model

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{Json, Reads}


object JSON {
  private val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val jodaDateReads = Reads[DateTime](js =>
    js.validate[String].map[DateTime](dtString =>
      DateTime.parse(dtString, DateTimeFormat.forPattern(dateFormat)).withZone(DateTimeZone.UTC)
    )
  )

  implicit val tagFormat = Json.format[Tag]

  implicit val relationshipFormat = Json.format[Relationship]
  implicit val configurationItemFormat = Json.format[ConfigurationItem]
  implicit val configurationItemDiffFormat = Json.format[ConfigurationItemDiff]
  implicit val invokingEventFormat = Json.format[InvokingEvent]

  implicit val userIdGroupPairFormat = Json.format[UserIdGroupPair]
  implicit val ipPermissionFormat = Json.format[IpPermission]
  implicit val sgConfigurationFormat = Json.format[SGConfiguration]
}
