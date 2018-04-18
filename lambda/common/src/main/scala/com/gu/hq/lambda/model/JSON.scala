package com.gu.hq.lambda.model

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{Json, Reads}


//noinspection TypeAnnotation
object JSON {
  private val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val jodaDateReads = Reads[DateTime](js =>
    js.validate[String].map[DateTime](dtString =>
      DateTime.parse(dtString, DateTimeFormat.forPattern(dateFormat)).withZone(DateTimeZone.UTC)
    )
  )

  implicit val tagRead = Json.reads[Tag]

  implicit val relationshipRead = Json.reads[Relationship]
  implicit val configurationItemRead = Json.reads[ConfigurationItem]
  implicit val configurationItemDiffRead = Json.reads[ConfigurationItemDiff]
  implicit val invokingEventRead = Json.reads[InvokingEvent]

  implicit val userIdGroupPairRead = Json.reads[UserIdGroupPair]
  implicit val ipPermissionRead = Json.reads[IpPermission]
  implicit val sgConfigurationRead = Json.reads[SGConfiguration]
  implicit val accountsRead = Json.reads[AccountMap]
}
