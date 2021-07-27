package schedule

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{AccessKeyMetadata, ListAccessKeysRequest, ListAccessKeysResult}
import model.{AccessKeyData, AwsAccount, VulnerableUser}
import org.joda.time.DateTime
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object IamListAccessKeys {
  def getAccessKeyData(keyData: Attempt[List[AccessKeyMetadata]], users: Seq[VulnerableUser])
    (implicit ec: ExecutionContext): (Attempt[List[AccessKeyData]], Seq[VulnerableUser]) =
    (keyData.map(getAccessKeyDetails), users)

  def getAccessKeyDetails(accessKeyMetadatas: List[AccessKeyMetadata]): List[AccessKeyData] = {
    accessKeyMetadatas.map { keyData =>
      AccessKeyData(
        keyData.getAccessKeyId,
        new DateTime(keyData.getCreateDate),
        keyData.getStatus,
        keyData.getUserName
      )
    }
  }

  // get the access key details for one user
  def listAccessKeys(client: AwsClient[AmazonIdentityManagementAsync], user: VulnerableUser)(implicit ec: ExecutionContext): Attempt[ListAccessKeysResult] = {
    val request = new ListAccessKeysRequest().withUserName(user.username)
    handleAWSErrs(client)(awsToScala(client)(_.listAccessKeysAsync)(request))
  }

  // get information on all access keys for a given AWS account
  def listAccountAccessKeys(account: AwsAccount, users: Seq[VulnerableUser], iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Attempt[List[AccessKeyMetadata]] = {
    val accessKeyData = users.map { user =>
      for {
        client <- iamClients.get(account, SOLE_REGION)
        keys <- listAccessKeys(client, user)
      } yield keys.getAccessKeyMetadata
    }.map(attempt => attempt.map(metadatas => metadatas.asScala.toList)).toList

    Attempt.sequence(accessKeyData).map(_.flatten)
  }
}
