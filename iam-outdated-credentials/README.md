# IAM Outdated Credentials lambda

A scheduled AWS Lambda that warns about, and eventually disables, IAM access keys that haven't been rotated recently
enough.

## What it does

An IAM access key is considered _outdated_ if it has not been rotated within the allowed cadence: 90 days for human
users, 365 days for machine users. Users tagged with the `SecurityHQ::OutdatedCredentialOptOut` tag are always
excluded, as a last resort for accounts where rotation is difficult.

On each invocation the job:

1. Loads the watched AWS accounts, allowed account IDs, the account IDs covered by the remediation service, the
   Anghammarad SNS topic ARN, and the IAM remediation DynamoDB table name from `security-hq.conf` in S3.
2. For every watched account, generates a fresh IAM credential report directly from IAM (there is no cache).
3. Identifies users with outdated access keys (and excludes opted-out users), restricted to the allowed accounts.
4. Looks up each identified user's remediation activity history in DynamoDB to work out what, if anything, has
   already been sent for that problem.
5. Based on how long the key has been outdated and what has already been sent, decides whether to: do nothing yet,
   send a warning, send a final warning, or remediate (disable the key). A final warning is sent 7 days after the
   first warning, and remediation occurs 7 days after that.
6. Remediation only actually runs on "Remediation Tuesday" (Tuesdays) for accounts covered by the remediation
   service; other accounts and days only ever warn.
7. Each remediation step is recorded in the DynamoDB remediation-activity table, so later invocations know not to
   repeat it.
8. Sends an Anghammarad notification for each warning/final warning/remediation, and publishes reaper metrics to
   CloudWatch.
9. <!-- alex ignore disable-disabled -->When `DRY_RUN` is enabled (the default), remediation is only logged — no access keys are actually disabled, though
   warning/final-warning notifications are still sent.

## Output

For each user with an outdated key, a real (non-dry-run) execution may, depending on the stage reached:

- send a **warning** or **final warning** Anghammarad notification to the relevant team;
- **disable the outdated IAM access key** (`UpdateAccessKey`), but only on "Remediation Tuesday" for accounts covered
  by the remediation service; and
- <!-- alex ignore disable-disabled -->send a **remediation** Anghammarad notification once the key has been disabled.

Each of these actions is recorded in the IAM remediation DynamoDB table so follow-up invocations don't repeat it.

Remediations are only performed on Tuesdays to try to ensure that any actions are only taken when teams will be working
(ie. avoiding bank holidays or Fridays), allowing them to respond to any outage caused by the automated removal of keys.

It also publishes reaper execution-status metrics (success/failure counts) to the `SecurityHQ` CloudWatch namespace,
defined in [`Cloudwatch`](../core/src/main/scala/com/gu/shq/logging/Cloudwatch.scala).

<!-- alex ignore disable-disabled -->
The metric name is `IamDisableOutdatedKeys`.

## Configuration

Configuration is provided via environment variables. Deployment is not yet wired up; once it is, these will be set by
the Security HQ CDK stack (`cdk/lib/security-hq.ts`):

| Variable        | Default      | Description                                                                   |
|-----------------|--------------|-------------------------------------------------------------------------------|
| `DRY_RUN`       | `true`       | When true, log remediation actions without disabling keys.                    |
| `STACK`         | _(required)_ | Stack name, used for tagging/labelling purposes.                              |
| `STAGE`         | _(required)_ | `PROD` or anything else (treated as `DEV`); determines the DynamoDB endpoint. |
| `CONFIG_BUCKET` | _(required)_ | Name of the Security account distribution bucket holding `security-hq.conf`.  |
| `CONFIG_KEY`    | _(required)_ | Path of `security-hq.conf` within that bucket.                                |

The AWS account list, allowed account IDs, remediation-service account IDs, Anghammarad SNS topic ARN, and IAM
remediation DynamoDB table name are all read from `security-hq.conf` in S3.

## Running locally

The lambda has a [main](src/main/scala/com/gu/shq/outdatedcredentials/Cli.scala) entrypoint to allow it to be run
locally for functional testing.

```sh
sbt "iamOutdatedCredentials / run"
```

The above configuration values are set inline in the entrypoint file.

### Required permissions

Running locally requires the **`Run Security HQ lambdas locally`** Janus permission for the `security` account.
Use `janus` to assume the role before running so that a `security` profile is available in `~/.aws/credentials`.

### Limitations when run locally

- Only the accounts listed in the (DEV) config referenced by `Main` are polled, so per-account coverage cannot be
  verified locally.
- No changes are ever made (`DRY_RUN` is hardcoded to `true`), only logged, so the 'would remediate' output should be
  checked in the IDE console rather than in AWS.
- The remediation DynamoDB table defaults to a local instance for `DEV` (see [Database](#database) below); make sure
  it is running before invoking locally.

### Database

The expected database for DEV is a local DynamoDB, which can be started with the provided docker compose file.
The DynamoDB for PROD is defined in the [security HQ stack](/cdk/lib/security-hq.ts)

## Troubleshooting

- **No accounts loaded / config parse errors**: check that `security-hq.conf` at `CONFIG_KEY` contains the expected
  `AWS_ACCOUNTS` block along with `ALLOWED_ACCOUNT_IDS`, `ACCOUNT_IDS_FOR_IAM_REMEDIATION_SERVICE`,
  `ANGHAMMARAD_SNS_TOPIC_ARN` and `IAM_DYNAMO_TABLE_NAME`.
- **Assume-role or credential-report failures for an account**: usually a missing reaper role / IAM permission in the
  target account, or throttling. Check the logged failure for the underlying cause.
- **Remediation never runs**: remediation only executes on Tuesdays for accounts listed in
  `ACCOUNT_IDS_FOR_IAM_REMEDIATION_SERVICE`; other accounts/days will only warn.

## Building

```sh
sbt iamOutdatedCredentials/assembly
```

produces `iam-outdated-credentials/target/scala-*/iam-outdated-credentials.jar`.
