#!/usr/bin/env bash
set -euo pipefail

awslocal dynamodb create-table \
  --table-name notifications \
  --attribute-definitions AttributeName=notificationId,AttributeType=S AttributeName=status,AttributeType=S \
  --key-schema AttributeName=notificationId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --global-secondary-indexes '[{"IndexName":"status-index","KeySchema":[{"AttributeName":"status","KeyType":"HASH"}],"Projection":{"ProjectionType":"ALL"}}]'

DLQ_ARN=$(awslocal sqs create-queue --queue-name notifications-dlq --query QueueUrl --output text | xargs -I{} awslocal sqs get-queue-attributes --queue-url {} --attribute-names QueueArn --query Attributes.QueueArn --output text)

awslocal sqs create-queue \
  --queue-name notifications-queue \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

awslocal sns create-topic --name notifications-push
