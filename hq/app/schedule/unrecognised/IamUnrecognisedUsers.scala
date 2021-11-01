package schedule.unrecognised

import com.gu.janus.model.JanusData
import model.{AwsAccount, CredentialReportDisplay, HumanUser, Tag, VulnerableUser}
import play.api.Logging
import utils.attempt.FailedAttempt

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files


object IamUnrecognisedUsers extends Logging {
  val USERNAME_TAG_KEY = "GoogleUsername"

  def getJanusUsernames(janusData: JanusData): List[String] =
    janusData.access.userAccess.keys.toList

  /**
    * Returns IAM permanent credentials for people who are not janus users.
    * Filters for the accounts the Security HQ stage has been configured for - see "alert.allowedAccountIds" in configuration.
    */
  def unrecognisedUsersForAllowedAccounts(
    accountCredsReports: List[(AwsAccount, CredentialReportDisplay)],
    janusUsernames: List[String],
    allowedAccountIds: List[String]
  ): List[(AwsAccount, List[VulnerableUser])] = {
    val unrecognisedUsers = accountCredsReports.map { case (acc, crd) => (acc, filterUnrecognisedIamUsers(crd.humanUsers, janusUsernames)) }
    unrecognisedIamUsersInAllowedAccounts(unrecognisedUsers, allowedAccountIds)
  }

  private def filterUnrecognisedIamUsers(iamHumanUsersWithTargetTag: Seq[HumanUser], janusUsernames: List[String]): List[VulnerableUser] =
    iamHumanUsersWithTargetTag.filterNot { iamUser =>
      val maybeTag = iamUser.tags.find(tag => tag.key == USERNAME_TAG_KEY)
      maybeTag match {
        case Some(tag) => janusUsernames.contains(tag.value) // filter out human users that have tags which match the janus usernames
        case None => true
      }
    }.map(VulnerableUser.fromIamUser).toList

  private def unrecognisedIamUsersInAllowedAccounts(
    unrecognisedUsers: List[(AwsAccount, List[VulnerableUser])],
    allowedAccountIds: List[String]
  ): List[(AwsAccount, List[VulnerableUser])] = {
    unrecognisedUsers.filter { case (account, _) => allowedAccountIds.contains(account.id) }
  }

  def makeFile(s3Object: String): File = {
    Files.write(
      Files.createTempFile("janusData", ".txt"),
      s3Object.getBytes(StandardCharsets.UTF_8)
    ).toFile
  }

    def isTaggedForUnrecognisedUser(tags: List[Tag]): Boolean = {
      tags.exists(t =>
        t.key == IamUnrecognisedUsers.USERNAME_TAG_KEY &&
          t.value != "" &&
          t.value.contains(".")
      )
    }
}
