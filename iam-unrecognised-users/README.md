# IAM Unrecognised Users lambda

A scheduled AWS Lambda that deactivates IAM users who are no longer recognised by Janus.

## What it does

An IAM 'human' user is considered _unrecognised_ if it is tagged with a `GoogleUsername` whose value is not present in the Guardian's Janus configuration. For each unrecognised user the lambda deactivates any active access keys, removes the login profile (password), and sends a notification to the relevant team via Anghammarad.

On each invocation the job:

1. Loads the watched AWS accounts, allowed account IDs, and Anghammarad SNS topic ARN from `security-hq.conf` in S3.
2. Fetches and parses the Janus configuration from S3 to build the canonical list of "recognised" usernames.
3. For every watched account, generates a fresh IAM credential report directly from IAM (there is no cache).
4. Identifies _unrecognised_ users — human IAM users tagged with a `GoogleUsername` that is not present in Janus — restricted to the allowed accounts.
5. For each unrecognised user, lists their access keys, then deactivates any active access keys and removes their login profile (password).
6. Sends an Anghammarad notification for each unrecognised user, and publishes reaper metrics to CloudWatch.
7. When `DRY_RUN` is enabled (the default), steps 5 and 6 only log what _would_ happen — no access keys are deactivated, no passwords removed, and no notifications sent.

## Output

For each unrecognised user, a real (non-dry-run) execution:

- deactivates any **active IAM access keys** (`UpdateAccessKey`);
- deletes the **login profile / console password** (`DeleteLoginProfile`); and
- sends an **Anghammarad notification** to the relevant team via the configured SNS topic.

It also publishes reaper execution-status metrics (success/failure counts) to the `SecurityHQ` CloudWatch namespace,
defined in [`Cloudwatch`](../core/src/main/scala/logging/Cloudwatch.scala).

<!-- alex ignore disable-disabled -->
The two metric names are `IamDisableAccessKey` (for the access-key step) and `IamRemovePassword` (for the password
step).

## Configuration

Configuration is provided via environment variables set by the CDK stack (`cdk/lib/iam-unrecognised-users.ts`):

| Variable                          | Default       | Description                                                                   |
|-----------------------------------|---------------|-------------------------------------------------------------------------------|
| `DRY_RUN`                         | `true`        | When true, log unrecognised users without deactivating or notifying.          |
| `CONFIG_BUCKET`                   | _(required)_  | Name of the Security account distribution bucket holding `security-hq.conf`.  |
| `CONFIG_KEY`                      | _(required)_  | Path of `security-hq.conf` within that bucket.                                |
| `IAM_UNRECOGNISED_USER_S3_BUCKET` | _(required)_  | S3 bucket holding the Janus data file (the audit-data bucket).                |
| `IAM_UNRECOGNISED_USER_S3_KEY`    | _(required)_  | S3 key (path) of the Janus data file.                                         |
| `REGION`                          | `eu-west-1`   | Primary region for owning-account clients and the config bucket.              |

The AWS account list, allowed account IDs, and Anghammarad SNS topic ARN are read from `security-hq.conf` in S3. The
Janus data location is provided separately via the `IAM_UNRECOGNISED_USER_S3_BUCKET` / `IAM_UNRECOGNISED_USER_S3_KEY`
environment variables (matching the Play app).

## Running locally

A local entrypoint is provided at [`Main.runUnrecognisedUsers`](src/main/scala/unrecognised/Main.scala), which can be
run from sbt:

```sh
sbt "iamUnrecognisedUsers/runMain unrecognised.runUnrecognisedUsers"
```

### Required environment

The locally run program reads `CONFIG_BUCKET` and `CONFIG_KEY` (the name of the Security account distribution bucket
and the path of `security-hq.conf` within it), plus `IAM_UNRECOGNISED_USER_S3_BUCKET` and `IAM_UNRECOGNISED_USER_S3_KEY`
(the audit-data bucket and the path of the Janus data file within it), from your local environment. Use the PROD paths
so the account list and Janus data stay consistent. `REGION` is optional (defaults to `eu-west-1`).

### Required permissions

Running locally currently requires the **`dev`** Janus permission for the `security` account. (This will soon change.)  
Use `janus` to assume the role before running so that a `security` profile is available in `~/.aws/credentials`.

### Limitations when run locally

- Only the `security` account is polled (`restrictToAccountId` is hardcoded), so per-account coverage cannot be
  verified locally.
- No changes are ever made (`DRY_RUN` is hardcoded), only logged, so the "would deactivate"/"would send" output should be
  checked in the IDE console rather than in AWS.

## Troubleshooting

- **No accounts loaded / config parse errors**: check that `security-hq.conf` at `CONFIG_KEY` contains the expected
  `AWS_ACCOUNTS` block along with `ALLOWED_ACCOUNT_IDS` and `ANGHAMMARAD_SNS_TOPIC_ARN`.
- **`Missing required environment variable …`**: one of the four required env vars is unset in the shell you launched
  `sbt` from — export them (see [Required environment](#required-environment)) and re-run in the same shell.
- **Assume-role or credential-report failures for an account**: usually a missing reaper role / IAM permission in the
  target account, or throttling. Check the logged failure for the underlying cause.
- **Janus data fails to load**: check that `IAM_UNRECOGNISED_USER_S3_BUCKET`/`_S3_KEY` point at the audit-data bucket and
  that the running role has `s3:GetObject` on it.

## Building

```sh
sbt iamUnrecognisedUsers/assembly
```

produces `iam-unrecognised-users/target/scala-*/iam-unrecognised-users.jar`.
