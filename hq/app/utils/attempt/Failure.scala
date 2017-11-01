package utils.attempt


case class FailedAttempt(failures: List[Failure]) {
  def statusCode: Int = failures.map(_.statusCode).max
  def logString: String = failures.map(_.message).mkString(", ")
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
  context: Option[String] = None
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

  def awsAccountNotFound(accountId: String): Failure = {
    Failure(
      s"Unknown account $accountId",
      s"Couldn't find AWS with ID $accountId",
      404
    )
  }
}
