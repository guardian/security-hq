package logic

import java.security.MessageDigest

import model.AwsAccount

object Colour {
  /**
    * Colours taken from those defined in materialize css.
    *
    * Teal is reserved for the site itself so we exclude that, grey is too boring.
    */
  val available = Seq(
    "red", "pink", "purple", "deep-purple", "indigo", "blue",
    "light-blue", "cyan", "green", "light-green", "lime",
    "yellow", "amber", "orange", "deep-orange", "brown",
    "blue-grey"
  )
  val availableCount = available.length

  def stableColour(identifier: String): String = {
    val distributedId = new String(MessageDigest.getInstance("MD5").digest(identifier.getBytes))
    available(distributedId.map(char => char.asDigit).sum % available.length)
  }

  def stableColour(awsAccount: AwsAccount): String = {
    stableColour(awsAccount.name + awsAccount.roleArn)
  }
}
