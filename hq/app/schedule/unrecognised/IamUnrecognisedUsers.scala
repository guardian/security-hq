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
    * Removes the FailedAttempts from the Either and returns a list of tuples with only the Right values.
    * This function uses generics to make it easier to test, but to avoid confusion it was written to take
    * a Map of AWSAccount to Either and return a list of tuples of AWS Account to CredentialReportDisplay.
    */
  def getCredsReportDisplayForAccount[A, B](allCreds: Map[A, Either[FailedAttempt, B]]): List[(A, B)] = {
    allCreds.toList.foldLeft[List[(A, B)]](Nil) {
      case (acc, (_, Left(failure))) =>
        logger.error(s"unable to generate credential report display: ${failure.logMessage}")
        acc
      case (acc, (account, Right(credReportDisplay))) =>
        (account, credReportDisplay) :: acc
    }
  }

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
    val unrecognisedUsersInAllowedAccounts = filterAllowedAccounts(unrecognisedUsers, allowedAccountIds)
    unrecognisedUsersInAllowedAccounts.filter { case (_, users) => users.nonEmpty} // remove accounts with an empty list of users
  }

  private def filterUnrecognisedIamUsers(iamHumanUsersWithTargetTag: Seq[HumanUser], janusUsernames: List[String]): List[VulnerableUser] =
    iamHumanUsersWithTargetTag.filterNot { iamUser =>
      val maybeTag = iamUser.tags.find(tag => tag.key == USERNAME_TAG_KEY)
      maybeTag match {
        case Some(tag) => janusUsernames.contains(tag.value) // filter out human users that have tags which match the janus usernames
        case None => true
      }
    }.map(VulnerableUser.fromIamUser).toList

  private def filterAllowedAccounts(
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
