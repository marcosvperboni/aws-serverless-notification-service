output "api_base_url" {
  value = aws_apigatewayv2_stage.default.invoke_url
}

output "notifications_table_name" {
  value = aws_dynamodb_table.notifications.name
}

output "notifications_queue_url" {
  value = aws_sqs_queue.notifications.id
}

output "notifications_dlq_url" {
  value = aws_sqs_queue.dlq.id
}

output "push_topic_arn" {
  value = aws_sns_topic.push.arn
}

output "ingest_lambda_name" {
  value = aws_lambda_function.ingest.function_name
}

output "processor_lambda_name" {
  value = aws_lambda_function.processor.function_name
}
