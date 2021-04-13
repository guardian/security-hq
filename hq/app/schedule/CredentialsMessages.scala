package schedule

import model.{UserNoMfa, UserWithOutdatedKeys}

object CredentialsMessages {
  val outdatedKeysMessage: String = "Please rotate the following AWS IAM access keys as they are over 90 days old and therefore pose a security risk:"
  val missingMfaMessage: String = "Please add multi-factor authentication to the following AWS IAM users:"
  val boilerPlateText: String =
    """
      |If you have any questions, please contact the Developer Experience team: devx@guardian.co.uk.
      |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/)
      |""".stripMargin

  // TODO: add tests for these createMessage functions
  def createMessage(outdatedKeys: Seq[UserWithOutdatedKeys], missingMfa: Seq[UserNoMfa]): String = {
    s"""
       |$outdatedKeysMessage
       |${outdatedKeys.map(printFormatOutdatedKeys)}
       |
       |$missingMfaMessage
       |${missingMfa.map(printFormatMissingMfa)}
       |
       |$boilerPlateText
       |""".stripMargin
  }
  def createMessage(outdatedKeys: Seq[UserWithOutdatedKeys]): String = {
    s"""
       |$outdatedKeysMessage
       |${outdatedKeys.map(printFormatOutdatedKeys)}
       |$boilerPlateText
       |""".stripMargin
  }
  //TODO understand why the compiler complains when Seq is used here instead of List
  def createMessage(missingMfa: List[UserNoMfa]): String = {
    s"""
       |$missingMfaMessage
       |${missingMfa.map(printFormatMissingMfa)}
       |$boilerPlateText
       |""".stripMargin
  }
  private def printFormatOutdatedKeys(user: UserWithOutdatedKeys): String = {
    s"""
      |Username: ${user.username}
      |Key 1 last rotation: ${user.key1LastRotation}
      |""".stripMargin
  }
  private def printFormatMissingMfa(user: UserNoMfa): String = ???
}
