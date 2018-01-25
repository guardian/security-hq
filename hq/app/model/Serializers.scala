package model

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}

trait Serializers {

  implicit val snykErrorFormat: Reads[SnykError] = Json.reads[SnykError]

  implicit val snykOrgFormat: Format[SnykOrg] = (
    (JsPath \ "name").format[String]
      and
      (JsPath \ "id").format[String]
    )(SnykOrg.apply, unlift(SnykOrg.unapply))

  implicit val snykProjectFormat: Format[SnykProject] = (
    (JsPath \ "name").format[String]
      and
      (JsPath \ "id").format[String]
    )(SnykProject.apply, unlift(SnykProject.unapply))

  implicit val snykIssueReads: Reads[SnykIssue] = (
    (JsPath \ "title").read[String]
      and
      (JsPath \ "id").read[String]
      and
      (JsPath \ "severity").read[String]
    )(SnykIssue.apply _)

  implicit val snykProjectIssuesReads: Reads[SnykProjectIssues] = (
    Reads.pure("Unknown")
      and
      Reads.pure("Unknown")
      and
      (JsPath \ "ok").read[Boolean]
      and
      (JsPath \ "issues" \ "vulnerabilities").read[List[SnykIssue]]
    )(SnykProjectIssues.apply _)


}
