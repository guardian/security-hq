package schedule.unrecognised

import com.gu.janus.model.JanusData
import model.{HumanUser, VulnerableUser}

object IamUnrecognisedUsers {
  val USERNAME_TAG_KEY = "GoogleUsername"

  def getJanusUsernames(janusData: JanusData): Seq[String] =
    janusData.access.userAccess.keys.toList

  def filterUnrecognisedIamUsers(iamHumanUsersWithTargetTag: Seq[HumanUser], janusUsernames: Seq[String]): Seq[VulnerableUser] =
    iamHumanUsersWithTargetTag.filterNot { iamUser =>
      val maybeTag = iamUser.tags.find(tag => tag.key == USERNAME_TAG_KEY)
      maybeTag match {
        case Some(tag) => janusUsernames.contains(tag.value) // filter out human users that have tags which match the janus usernames
        case None => true
      }
    }.map(VulnerableUser.fromIamUser)
}
