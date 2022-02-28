package logic

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{AccessKeyMetadata, ListAccessKeysRequest, ListAccessKeysResult}
import model.{AccessKeyWithId, AccountUnrecognisedAccessKeys, AccountUnrecognisedUsers, AwsAccount, HumanUser, VulnerableAccessKey}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

//TODO: split up and move this into IAMClient/IAMOutdatedCredentials as appropriate
object IamListAccessKeys {

  // get information on all access keys for a given AWS account
  def listAccountAccessKeys(accountUsers: AccountUnrecognisedUsers, iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Attempt[AccountUnrecognisedAccessKeys] = {
    val accessKeyData = accountUsers.unrecognisedUsers.map { user =>
      for {
        client <- iamClients.get(accountUsers.account, SOLE_REGION)
        keys <- listAccessKeys(client, user)
      } yield keys.getAccessKeyMetadata.asScala.toList
    }
    //TODO: we can probably improve this further, but just to get it compiling for now
    Attempt.flatSequence(accessKeyData).map { keyData =>
      AccountUnrecognisedAccessKeys(accountUsers.account, addAccessKeyIds(accountUsers.unrecognisedUsers, keyData))
    }
  }

  private def addAccessKeyIds(users: Seq[HumanUser], accessKeyData: List[AccessKeyMetadata]): List[VulnerableAccessKey] = {
    /*
   Our vulnerable users are taken from an AWS "Credentials Report" which does not include Access Key IDs,
   so here we add take the metadata from our retrieved AccessKeyMetadata and combine with user metadata
   to create a more useful VulnerableAccessKey type
  */
    for {
      accessKey <- accessKeyData
      user <- users.find(_.username == accessKey.getUserName)
    } yield {
      VulnerableAccessKey(user.username, AccessKeyWithId.fromAwsAccessKeyMetadata(accessKey), user.isHuman)
    }
  }

  // get the access key details for one user
  private def listAccessKeys(client: AwsClient[AmazonIdentityManagementAsync], user: HumanUser)
    (implicit ec: ExecutionContext): Attempt[ListAccessKeysResult] = {
    val request = new ListAccessKeysRequest().withUserName(user.username)
    handleAWSErrs(client)(awsToScala(client)(_.listAccessKeysAsync)(request))
  }
}
