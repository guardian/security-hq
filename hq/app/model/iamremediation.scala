package model

import org.joda.time.DateTime

/*
 * Database representations for the IAMRemediation functionality.
 *
 * These DB records are to keep track of whether users have been notified about IAM problems
 * ahead of SHQ's automatic interventions.
 */

case class IamRemediationActivity(
  id: String,
  awsAccount: String,
  dateNotificationSent: DateTime,
  username: String,
  iamNotificationType: IamRemediationNotificationType,
  iamProblem: IamProblem,
  problemCreationDate: DateTime,  // the age of the password / credential, allows us to check if previous notifications still apply
)

sealed trait IamProblem
case object OutdatedCredential extends IamProblem
case object PasswordMissingMFA extends IamProblem

sealed trait IamRemediationNotificationType
case object Warning extends IamRemediationNotificationType
case object FinalWarning extends IamRemediationNotificationType
