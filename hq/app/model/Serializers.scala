package model

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.Logging
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.Try

object Serializers extends Logging {

  implicit val snykErrorFormat: Reads[SnykError] = Json.reads[SnykError]

  implicit val snykGroupFormat: Reads[SnykGroup] = Json.reads[SnykGroup]

  implicit val snykOrgFormat: Reads[SnykOrganisation] = (
    (JsPath \ "name").read[String]
      and
      (JsPath \ "id").read[String]
      and
      (JsPath \ "group").readNullable[SnykGroup]
    ) (SnykOrganisation.apply _)

  implicit val snykProjectFormat: Reads[SnykProject] = Json.reads[SnykProject]

  implicit val snykIssueReads: Format[SnykIssue] = Json.format[SnykIssue]

  private val snykDateReads = new Reads[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] =
      Try {
        ISODateTimeFormat.dateParser().parseDateTime(json.as[String])
      } fold(
        _ => JsError("Unable to parse date string from Snyk API"),
        date => JsSuccess(date)
      )
  }

  implicit val snykProjectIssueReads: Reads[SnykProjectIssue] = (
    (JsPath \ "project").readNullable[SnykProject]
      and
      (JsPath \ "introducedDate").read(snykDateReads)
      and
      (JsPath \ "issue").read[SnykIssue]
    ) (SnykProjectIssue.apply _)
}
