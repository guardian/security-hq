package model

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}

object Serializers {

  implicit val snykErrorFormat: Reads[SnykError] = Json.reads[SnykError]

  implicit val snykOrgFormat: Reads[SnykOrganisation] = Json.reads[SnykOrganisation]

  implicit val snykProjectFormat: Reads[SnykProject] = (
    (JsPath \ "name").read[String]
      and
      (JsPath \ "id").read[String]
      and
      Reads.pure(None)
    )(SnykProject.apply _)

  implicit val snykIssueReads: Format[SnykIssue] = Json.format[SnykIssue]

  implicit val snykProjectIssuesReads: Reads[SnykProjectIssues] = (
    Reads.pure(None)
      and
      (JsPath \ "ok").read[Boolean]
      and
      (JsPath \ "issues" \ "vulnerabilities").read[List[SnykIssue]]
    )(SnykProjectIssues.apply _)

}
