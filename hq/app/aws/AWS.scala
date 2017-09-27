package aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, STSAssumeRoleSessionCredentialsProvider}
import model.AwsAccount
import utils.attempt.{Attempt, Failure}


object AWS {
  def credentialsProvider(account: AwsAccount): AWSCredentialsProviderChain = {
    new AWSCredentialsProviderChain(
      new STSAssumeRoleSessionCredentialsProvider.Builder(account.roleArn, "security-hq").build(),
      new ProfileCredentialsProvider(account.id)
    )
  }

  def lookupAccount(accountId: String, accounts: List[AwsAccount]): Attempt[AwsAccount] = {
    Attempt.fromOption(
      accounts.find(_.id == accountId),
      Failure.awsAccountNotFound(accountId).attempt
    )
  }
}
