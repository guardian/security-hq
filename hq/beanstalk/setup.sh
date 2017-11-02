#!/usr/bin/env bash
# on-instance setup, fetches application configuration

ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

touch /tmp/test-setup-ran

aws s3 cp s3://$HQ_CONFIG_BUCKET/$HQ_CONFIG_PATH $ROOT
aws s3 cp s3://$HQ_CONFIG_BUCKET/$HQ_CERT_PATH $ROOT
