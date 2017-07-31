#!/usr/bin/env bash
# on-instance setup, fetches application configuration

aws s3 cp s3::$HQ_CONFIG_BUCKET/$HQ_CONFIG_PATH .
