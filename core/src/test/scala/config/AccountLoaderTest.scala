package config

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues

class AccountLoaderTest extends AnyFreeSpec with Matchers with OptionValues {

  "getAwsAccountsFromString" - {
    val hocon =
      """
        |AWS_ACCOUNTS = [
        |  { id = "b-account", name = "B Account", roleArn = "arn:b", number = "222" }
        |  { id = "a-account", name = "A Account", roleArn = "arn:a", number = "111" }
        |]
        |""".stripMargin

    "parses all accounts" in {
      AccountLoader.getAwsAccountsFromString(hocon) should have length 2
    }

    "maps every field, including number -> accountNumber" in {
      val accounts = AccountLoader.getAwsAccountsFromString(hocon)
      val a = accounts.find(_.id == "a-account").value
      a.name shouldEqual "A Account"
      a.roleArn shouldEqual "arn:a"
      a.accountNumber shouldEqual "111"
    }

    "sorts accounts by name" in {
      AccountLoader
        .getAwsAccountsFromString(hocon)
        .map(_.name) shouldEqual List("A Account", "B Account")
    }

    "skips accounts missing required fields" in {
      val partial =
        """
          |AWS_ACCOUNTS = [
          |  { id = "ok", name = "Ok", roleArn = "arn", number = "1" }
          |  { id = "missing-number", name = "No Number", roleArn = "arn" }
          |]
          |""".stripMargin
      AccountLoader
        .getAwsAccountsFromString(partial)
        .map(_.id) shouldEqual List("ok")
    }
  }
}
