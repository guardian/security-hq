package notifications

import com.gu.anghammarad.models.Notification
import model.{AwsAccount, IAMUser}
import org.joda.time.DateTime

object IamRemediationNotifications {
  def outdatedCredentialWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  def passwordWithoutMfaWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  def outdatedCredentialFinalWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  def passwordWithoutMfaFinalWarning(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  def outdatedCredentialRemediation(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }

  def passwordWithoutMfaRemediation(awsAccount: AwsAccount, iamUser: IAMUser, problemCreationDate: DateTime): Notification = {
    ???
  }
}
