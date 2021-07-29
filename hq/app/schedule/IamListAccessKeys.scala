package schedule

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{AccessKeyMetadata, ListAccessKeysRequest, ListAccessKeysResult}
import model.{AccessKeyWithId, AwsAccount, VulnerableUser, VulnerableUserWithAccessKeyId}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object IamListAccessKeys {

  // get information on all access keys for a given AWS account
  def listAccountAccessKeys(account: AwsAccount, users: Seq[VulnerableUser], iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Attempt[List[VulnerableUserWithAccessKeyId]] = {
    val accessKeyData = users.map { user =>
      for {
        client <- iamClients.get(account, SOLE_REGION)
        keys <- listAccessKeys(client, user)
      } yield keys.getAccessKeyMetadata
    }.map(attempt => attempt.map(metadatas => metadatas.asScala.toList)).toList

    Attempt.sequence(accessKeyData).map(_.flatten).map(accessKeyMetadatas => addAccessKeysToUsers(accessKeyMetadatas, users))
  }

  def addAccessKeysToUsers(accessKeyData: List[AccessKeyMetadata], users: Seq[VulnerableUser]): List[VulnerableUserWithAccessKeyId] = {
    addAccessKeyIds(users.filter(_.humanUser), accessKeyData, humanUser = true).toList ++
      addAccessKeyIds(users.filterNot(_.humanUser), accessKeyData, humanUser = false)
  }

  private def addAccessKeyIds(users: Seq[VulnerableUser], accessKeyData: List[AccessKeyMetadata], humanUser: Boolean): Seq[VulnerableUserWithAccessKeyId] = {
    users.flatMap { user =>
      accessKeyData.filter(_.getUserName == user.username).map(_.getAccessKeyId).map { id =>
        VulnerableUserWithAccessKeyId(user.username, AccessKeyWithId(user.key1.keyStatus, user.key1.lastRotated, id), humanUser)
      }
    }
  }

  // get the access key details for one user
  def listAccessKeys(client: AwsClient[AmazonIdentityManagementAsync], user: VulnerableUser)
    (implicit ec: ExecutionContext): Attempt[ListAccessKeysResult] = {
    val request = new ListAccessKeysRequest().withUserName(user.username)
    handleAWSErrs(client)(awsToScala(client)(_.listAccessKeysAsync)(request))
  }
}
