package model

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}

object Serializers {

  implicit val snykErrorFormat: Reads[SnykError] = Json.reads[SnykError]

  implicit val snykOrgFormat: Format[SnykOrg] = Json.format[SnykOrg]

  implicit val snykProjectFormat: Format[SnykProject] = Json.format[SnykProject]

  implicit val snykIssueReads: Format[SnykIssue] = Json.format[SnykIssue]

  implicit val snykProjectIssuesReads: Reads[SnykProjectIssues] = (
    Reads.pure("Unknown")
    and Reads.pure("Unknown")
    and (JsPath \ "ok").read[Boolean]
    and (JsPath \ "issues" \ "vulnerabilities").read[List[SnykIssue]]
    )(SnykProjectIssues.apply _)

}
