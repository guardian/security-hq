package schedule

import aws.AwsClients
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileResult, ListAccessKeysResult, UpdateAccessKeyResult}
import model.IamAuditUser
import play.api.Logging
import utils.attempt.Attempt

object IamDisable extends Logging {
  // TODO: follow pattern: getAllCredentialReports

  // https://docs.aws.amazon.com/code-samples/latest/catalog/java-iam-src-main-java-aws-example-iam-UpdateAccessKey.java.html
  // to disable an access key, we need the access key id - call getAccessKey
  def disableAccessKey(iamClients: AwsClients[AmazonIdentityManagementAsync], user: IamAuditUser): Attempt[UpdateAccessKeyResult] = ???

  // https://docs.aws.amazon.com/code-samples/latest/catalog/java-iam-src-main-java-aws-example-iam-ListAccessKeys.java.html
  // TODO - how can we get the right access key id, because each user can have two?
  def getAccessKey(iamClients: AwsClients[AmazonIdentityManagementAsync], user: IamAuditUser): Attempt[ListAccessKeysResult] = ???

  // https://docs.aws.amazon.com/IAM/latest/APIReference/API_DeleteLoginProfile.html
  def deletePassword(iamClients: AwsClients[AmazonIdentityManagementAsync], user: IamAuditUser): Attempt[DeleteLoginProfileResult] = ???
}
