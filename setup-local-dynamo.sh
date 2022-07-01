#!/usr/bin/env bash
# starts up a local dynamoDB server and creates local table
# useful for testing IamJob, which saves state in dynamoDB.

docker run -p 8000:8000 amazon/dynamodb-local

aws dynamodb create-table --table-name security-hq-iam-DEV \
--attribute-definitions AttributeName=id,AttributeType=S \
--key-schema AttributeName=id,KeyType=HASH \
--billing-mode PAY_PER_REQUEST \
--endpoint-url http://localhost:8000 \
--region eu-west-1

# Use this client in AppComponents.scala in place of existing dynamoDbClient:
# AmazonDynamoDBClientBuilder.standard()
#  .withCredentials(securityCredentialsProvider)
#  .withEndpointConfiguration(new EndpointConfiguration("http://127.0.0.1:8000", Config.region.getName))
#  .build()

# query local table with the following command:
# aws dynamodb scan --table-name security-hq-iam-DEV \
# --endpoint-url http://127.0.0.1:8000 \
# --region eu-west-1