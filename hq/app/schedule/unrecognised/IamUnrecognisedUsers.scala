package schedule.unrecognised

import com.gu.janus.model.JanusData
import model.{AwsAccount, CredentialReportDisplay, HumanUser, Tag}
import utils.attempt.FailedAttempt

object IamUnrecognisedUsers {
  def getJanusUsernames(janusData: JanusData): List[String] =
    janusData.access.userAccess.keys.toList

  def getHumanUsers(credsReport: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]): List[HumanUser] =
    credsReport.values.collect { case Right(report) => report.humanUsers }.flatten.toList

  def filterUnrecognisedIamUsers(iamHumanUsersWithTargetTag: List[HumanUser], targetKey: String, janusUsernames: List[String]): List[HumanUser] =
    iamHumanUsersWithTargetTag.filterNot { iamUser =>
      val maybeTag = iamUser.tags.find(tag => tag.key == targetKey)
      maybeTag match {
        case Some(tag) => janusUsernames.contains(tag.value) // filter out human users that have tags which match the janus usernames
        case None => true
      }
    }
}
