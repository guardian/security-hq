package aws

import model.AwsAccount
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient

import java.util.concurrent.{ExecutorService, TimeUnit}

/** Bundles the credentials provider used to build a client together with any resources created specifically to support
  * it (for example the [[StsClient]] backing an `StsAssumeRoleCredentialsProvider`), so that both can be released
  * together.
  *
  * Closing `provider` does not close `stsClient`, since the STS SDK does not take ownership of a client supplied
  * explicitly via `.stsClient(...)`; both must therefore be closed explicitly here.
  */
final case class ClientCredentials(provider: AwsCredentialsProviderChain, stsClient: StsClient) extends AutoCloseable {
  override def close(): Unit =
    try provider.close()
    finally stsClient.close()
}

/** Wraps an [[ExecutorService]] created specifically to back a single async client's completion callbacks, so that it
  * can be shut down deterministically when the client is closed.
  *
  * `shutdownNow()` (rather than the gentler `shutdown()`) is used deliberately: a cached thread pool's idle worker
  * threads are typically parked in a bounded `poll` on the work queue and only notice a plain `shutdown()` once that
  * poll's keep-alive timeout (60 seconds by default) elapses. Interrupting them makes shutdown near-instant instead of
  * leaving non-daemon threads around for up to a minute after the client is otherwise finished with.
  */
final case class ClientExecutor(executor: ExecutorService) extends AutoCloseable {
  override def close(): Unit = {
    executor.shutdownNow()
    executor.awaitTermination(5, TimeUnit.SECONDS)
    ()
  }
}

/** @param credentials
  *   the credentials (and any supporting resources) used to build `client`, if they were constructed specifically for
  *   this client. Exposed so that callers which manage the full lifecycle of a client (for example short-lived Lambda
  *   invocations) can also release any background resources the credentials hold. Long-running callers, such as the
  *   main web application, are free to leave this unused and let the credentials live for as long as the client does.
  * @param executor
  *   the executor service created to back this client's async completions, if one was created specifically for it. As
  *   with `credentials`, only short-lived callers typically need to close this explicitly.
  */
case class AwsClient[A](
    client: A,
    account: AwsAccount,
    region: Region,
    credentials: Option[ClientCredentials] = None,
    executor: Option[ClientExecutor] = None
)
