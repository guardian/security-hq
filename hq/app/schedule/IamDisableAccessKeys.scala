package schedule

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{ListAccessKeysRequest, ListAccessKeysResult, UpdateAccessKeyResult}
import model.{AwsAccount, IamAuditUser}
import org.joda.time.{DateTime, Days}
import play.api.Logging
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

object IamDisableAccessKeys extends Logging {
  // TODO: follow pattern: getAllCredentialReports

  // https://docs.aws.amazon.com/code-samples/latest/catalog/java-iam-src-main-java-aws-example-iam-UpdateAccessKey.java.html
  // to disable an access key, we need the access key id - call getAccessKey
  // each user has an AWS account
  def disableAccessKey(account: AwsAccount, users: Seq[IamAuditUser], iamClients: AwsClients[AmazonIdentityManagementAsync]): Attempt[UpdateAccessKeyResult] = ???

  // https://docs.aws.amazon.com/code-samples/latest/catalog/java-iam-src-main-java-aws-example-iam-ListAccessKeys.java.html
  def getAccessKey(iamClients: AwsClients[AmazonIdentityManagementAsync], user: IamAuditUser): Attempt[ListAccessKeysResult] = ???


  // get access keys for a given user
  // error handling
  //TODO
  def listAccountAccessKeys(account: AwsAccount, users: Seq[IamAuditUser], iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext) = {
    users.map { user =>
      logger.info(s"attempting to get access keys for user ${user.username} in AWS account ${user.awsAccount}")
      for {
        client <- iamClients.get(account, SOLE_REGION)
        keys <- listAccessKeys(client, user)
      } yield keys.getAccessKeyMetadata
    }
  }

  def listAccessKeys(client: AwsClient[AmazonIdentityManagementAsync], user: IamAuditUser)(implicit ec: ExecutionContext): Attempt[ListAccessKeysResult] = {
    val request = new ListAccessKeysRequest().withUserName(user.username)
    handleAWSErrs(client)(awsToScala(client)(_.listAccessKeysAsync)(request))
  }

  // get the access key that was rotated longest ago out of the 2 available to each user
  def whichRotationDateIsOlder(date1: Option[DateTime], date2: Option[DateTime]): Option[DateTime] = {
    (date1, date2) match {
      case (None, None) => None
      case (None, Some(dt2)) => Some(dt2)
      case (Some(dt1), None) => Some(dt1)
      case (Some(dt1), Some(dt2)) => {
        val today = DateTime.now.withTimeAtStartOfDay
        val d1 = Days.daysBetween(dt1, today).getDays
        val d2 = Days.daysBetween(dt2, today).getDays
        if (d1 > d2) Some(dt1) else Some(dt2)
      }
    }
  }

  // addresses case when both access keys are enabled and are the same date as both may need to be disabled
  def areRotationDatesEqual(date1: DateTime, date2: DateTime): Boolean = date1.withTimeAtStartOfDay == date2.withTimeAtStartOfDay
}
