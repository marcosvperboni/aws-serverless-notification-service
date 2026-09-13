locals {
  lambda_environment_variables = {
    NOTIFICATION_TABLE_NAME     = aws_dynamodb_table.notifications.name
    NOTIFICATION_QUEUE_URL      = aws_sqs_queue.notifications.id
    NOTIFICATION_SES_SENDER     = var.ses_sender_address
    NOTIFICATION_PUSH_TOPIC_ARN = aws_sns_topic.push.arn
    NOTIFICATION_MAX_ATTEMPTS   = tostring(var.sqs_max_receive_count)
  }
}

resource "aws_lambda_function" "ingest" {
  function_name    = "${local.name_prefix}-ingest"
  filename         = var.lambda_jar_path
  source_code_hash = filebase64sha256(var.lambda_jar_path)
  handler          = "com.marcosperboni.notification.handler.IngestNotificationHandler::handleRequest"
  runtime          = var.lambda_runtime
  role             = aws_iam_role.ingest.arn
  memory_size      = var.lambda_memory_size
  timeout          = var.lambda_timeout

  environment {
    variables = local.lambda_environment_variables
  }

  depends_on = [aws_cloudwatch_log_group.ingest]

  tags = local.common_tags
}

resource "aws_lambda_function" "processor" {
  function_name    = "${local.name_prefix}-processor"
  filename         = var.lambda_jar_path
  source_code_hash = filebase64sha256(var.lambda_jar_path)
  handler          = "com.marcosperboni.notification.handler.ProcessNotificationHandler::handleRequest"
  runtime          = var.lambda_runtime
  role             = aws_iam_role.processor.arn
  memory_size      = var.lambda_memory_size
  timeout          = var.lambda_timeout

  environment {
    variables = local.lambda_environment_variables
  }

  depends_on = [aws_cloudwatch_log_group.processor]

  tags = local.common_tags
}

resource "aws_lambda_event_source_mapping" "notifications_queue" {
  event_source_arn = aws_sqs_queue.notifications.arn
  function_name    = aws_lambda_function.processor.arn
  batch_size       = 10
}
