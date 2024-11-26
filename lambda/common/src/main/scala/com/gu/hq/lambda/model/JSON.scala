package com.gu.hq.lambda.model

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{Json, Reads}


//noinspection TypeAnnotation
object JSON {
  private val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  implicit val jodaDateReads: Reads[DateTime] = Reads[DateTime](js =>
    js.validate[String].map[DateTime](dtString =>
      DateTime.parse(dtString, DateTimeFormat.forPattern(dateFormat)).withZone(DateTimeZone.UTC)
    )
  )

  implicit val tagRead: Reads[Tag] = Json.reads[Tag]

  implicit val relationshipRead: Reads[Relationship] = Json.reads[Relationship]
  implicit val configurationItemRead: Reads[ConfigurationItem] = Json.reads[ConfigurationItem]
  implicit val configurationItemDiffRead: Reads[ConfigurationItemDiff] = Json.reads[ConfigurationItemDiff]
  implicit val invokingEventRead: Reads[InvokingEvent] = Json.reads[InvokingEvent]

  implicit val userIdGroupPairRead: Reads[UserIdGroupPair] = Json.reads[UserIdGroupPair]
  implicit val ipPermissionRead: Reads[IpPermission] = Json.reads[IpPermission]
  implicit val sgConfigurationRead: Reads[SGConfiguration] = Json.reads[SGConfiguration]
  implicit val accountsRead: Reads[AccountMap] = Json.reads[AccountMap]
}
