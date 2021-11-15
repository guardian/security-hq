package model

import org.joda.time.DateTime


/**
  * Description of an IAM user with the remediation activity SHQ has previously performed for that user.
  */
case class IamUserRemediationHistory(
  awsAccount: AwsAccount,
  iamUser: IAMUser,
  activityHistory: List[IamRemediationActivity],
)

/**
  * An IAM remediation operation that was performed in the past.
  *
  * This case class is used as a database record.
  *
  * These DB records are to keep track of whether users have been notified about IAM problems
  * ahead of SHQ's automatic interventions.
  */
case class IamRemediationActivity(
  // in the DB, primary key is a composite, equal to s"$awsAccountId/$username"
  awsAccountId: String,
  username: String,
  dateNotificationSent: DateTime, // range key in the DB
  iamRemediationActivityType: IamRemediationActivityType,
  iamProblem: IamProblem,
  problemCreationDate: DateTime,  // the age of the password / credential, allows us to check if previous notifications still apply
)

/**
  * Represents a potential remediation operation
  */
case class RemediationOperation(
  vulnerableCandidate: IamUserRemediationHistory,
  iamRemediationActivityType: IamRemediationActivityType,
  iamProblem: IamProblem,
  problemCreationDate: DateTime,
)

/**
  * This case class has descriptive fieldnames to document the intent, which is
  * to prevent operations being performed on AWS accounts that are not allowed by
  * the application's configuration.
  */
case class PartitionedRemediationOperations(
  allowedOperations: List[RemediationOperation],
  operationsOnAccountsThatAreNotAllowed: List[RemediationOperation]
)

sealed trait IamProblem
case object OutdatedCredential extends IamProblem
case object PasswordMissingMFA extends IamProblem

sealed trait IamRemediationActivityType
case object Warning extends IamRemediationActivityType
case object FinalWarning extends IamRemediationActivityType
case object Remediation extends IamRemediationActivityType

/**
  * To disable credentials we need the accessKeyId, which is not available in the credentials report.
  * CredentialMetadata represents the information we get back from the list-access-keys AWS API call.
  */
case class CredentialMetadata(
  username: String,
  accessKeyId: String,
  creationDate: DateTime,
  status: CredentialStatus
)

sealed trait CredentialStatus
case object CredentialActive extends CredentialStatus
case object CredentialDisabled extends CredentialStatus