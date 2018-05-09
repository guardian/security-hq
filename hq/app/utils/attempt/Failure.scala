package utils.attempt


case class FailedAttempt(failures: List[Failure]) {
  def statusCode: Int = failures.map(_.statusCode).max

  def logMessage: String = failures.map { failure =>
    val context = failure.context.fold("")(c => s" ($c)")
    s"${failure.message}$context"
  }.mkString(", ")

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

case class Failure(
  message: String,
  friendlyMessage: String,
  statusCode: Int,
  context: Option[String] = None,
  throwable: Option[Throwable] = None
) {
  def attempt = FailedAttempt(this)
}
object Failure {
  // Pre-defined "common" failures

  def awsError(serviceNameOpt: Option[String]): Failure = {
    val details = serviceNameOpt.fold("AWS unknown error, unknown service (check logs for stacktrace)") { serviceName =>
      s"AWS unknown error, service: $serviceName (check logs for stacktrace)"
    }
    val friendlyMessage = serviceNameOpt.fold("Unknown error while making API calls to AWS.") { serviceName =>
      s"Unknown error while making an API call to AWS' $serviceName service"
    }
    Failure(details, friendlyMessage, 500)
  }

  def cacheServiceErrorPerAccount(accountId: String, cacheContent: String): Failure = {
    val details = s"Cache service error; unable to retrieve $cacheContent for $accountId"
    val friendlyMessage = s"Missing $cacheContent data for $accountId"
    Failure(details, friendlyMessage, 500)
  }

  def cacheServiceErrorAllAccounts(cacheContent: String): Failure = {
    val details = s"Cache service error; unable to retrieve $cacheContent"
    val friendlyMessage = s"Missing $cacheContent data"
    Failure(details, friendlyMessage, 500)
  }

  def expiredCredentials(serviceNameOpt: Option[String]): Failure = {
    val details = serviceNameOpt.fold("expired AWS credentials, unknown service") { serviceName =>
      s"expired AWS credentials, service: $serviceName"
    }
    Failure(details, "Failed to request data from AWS, the temporary credentials have expired.", 401)
  }

  def noCredentials(serviceNameOpt: Option[String]): Failure = {
    val details = serviceNameOpt.fold("no AWS credentials available, unknown service") { serviceName =>
      s"no credentials found, service: $serviceName"
    }
    Failure(details, "Failed to request data from AWS, no credentials found.", 401)
  }

  def insufficientPermissions(serviceNameOpt: Option[String]): Failure = {
    val details = serviceNameOpt.fold("application is not authorized to perform actions for a service") { serviceName =>
      s"application is not authorized to perform actions for service: $serviceName"
    }
    val friendlyMessage = serviceNameOpt.fold("Application is not authorized to perform actions for a service") { serviceName =>
      s"Application is not authorized to perform actions for service: $serviceName by the current access policies"
    }
    Failure(details, friendlyMessage, 403)
  }

  def rateLimitExceeded(serviceNameOpt: Option[String]): Failure = {
    val details = serviceNameOpt.fold("rate limit exceeded while calling an AWS service") { serviceName =>
      s"rate limit exceeded while calling service: $serviceName"
    }
    val friendlyMessage = serviceNameOpt.fold("Rate limit exceeded") { serviceName =>
      s"Rate limit exceeded for service: $serviceName"
    }
    Failure(details, friendlyMessage, 429)
  }

  def awsAccountNotFound(accountId: String): Failure = {
    Failure(
      s"Unknown account $accountId",
      s"Couldn't find AWS with ID $accountId",
      404
    )
  }
}
