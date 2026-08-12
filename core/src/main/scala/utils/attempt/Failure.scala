package utils.attempt

import aws.AwsClient

case class FailedAttempt(failures: List[Failure]) {

  def logMessage: String = failures
    .map { failure =>
      val causedBy = firstException.fold("")(err => s" caused by: ${err.getMessage}")
      s"${failure.message}$causedBy"
    }
    .mkString(", ")

  def firstException: Option[Throwable] = {
    for {
      exceptingFailure <- failures.find(_.throwable.isDefined)
      throwable <- exceptingFailure.throwable
    } yield throwable
  }
}
object FailedAttempt {
  def apply(error: Failure): FailedAttempt = {
    FailedAttempt(List(error))
  }
  def apply(errors: Seq[Failure]): FailedAttempt = {
    FailedAttempt(errors.toList)
  }
}

// This case class was originally created to prettify HTTP responses.
//
// It has had all the code removed that made it HTTP friendly because we no
// longer have an app.
//
// It could be replaced with an exception.  However, returning an exception
// is not nice, and throwing it would change the execution path.
//
// So we keep it around for now.
case class Failure(
    message: String,
    throwable: Option[Throwable] = None
) {
  def attempt = FailedAttempt(this)
}
object Failure {
  // Pre-defined "common" failures

  def awsError(serviceNameOpt: Option[String], clientContext: AwsClient[_], err: Throwable): Failure = {
    val context = contextString(clientContext)
    val details = serviceNameOpt.fold(s"AWS unknown error, unknown service (check logs for stacktrace), $context") {
      serviceName =>
        s"AWS unknown error, service: $serviceName (check logs for stacktrace), $context"
    }
    Failure(details, throwable = Some(err))
  }

  def notYetLoaded(accountId: String, cacheContent: String): Failure = {
    val details = s"Cache service error; $cacheContent not yet loaded for $accountId"
    Failure(details)
  }

  private def contextString(clientContext: AwsClient[_]): String = {
    val acc = s"account: ${clientContext.account.name}"
    val reg = s"region: ${clientContext.region.id}"
    s"$acc, $reg"
  }

  def expiredCredentials(serviceNameOpt: Option[String], clientContext: AwsClient[_]): Failure = {
    val context = contextString(clientContext)
    val details = serviceNameOpt.fold(s"expired AWS credentials, unknown service, $context") { serviceName =>
      s"expired AWS credentials, service: $serviceName, $context"
    }
    Failure(details)
  }

  def noCredentials(serviceNameOpt: Option[String], clientContext: AwsClient[_]): Failure = {
    val context = contextString(clientContext)
    val details = serviceNameOpt.fold(s"no AWS credentials available, unknown service, $context") { serviceName =>
      s"no credentials found, service: $serviceName, $context"
    }
    Failure(details)
  }

  def insufficientPermissions(serviceNameOpt: Option[String], clientContext: AwsClient[_]): Failure = {
    val context = contextString(clientContext)
    val details = serviceNameOpt.fold(s"application is not authorized to perform actions for a service, $context") {
      serviceName =>
        s"application is not authorized to perform actions for service: $serviceName, $context"
    }
    Failure(details)
  }

  def rateLimitExceeded(serviceNameOpt: Option[String], clientContext: AwsClient[_]): Failure = {
    val context = contextString(clientContext)
    val details = serviceNameOpt.fold(s"rate limit exceeded while calling an AWS service, $context") { serviceName =>
      s"rate limit exceeded while calling service: $serviceName, $context"
    }
    Failure(details)
  }
}
