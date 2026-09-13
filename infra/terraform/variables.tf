variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "project_name" {
  type    = string
  default = "notification-service"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "lambda_jar_path" {
  type    = string
  default = "../../target/aws-serverless-notification-service-0.0.1-SNAPSHOT-aws.jar"
}

variable "lambda_runtime" {
  type    = string
  default = "java25"
}

variable "lambda_memory_size" {
  type    = number
  default = 512
}

variable "lambda_timeout" {
  type    = number
  default = 15
}

variable "sqs_max_receive_count" {
  type    = number
  default = 3
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "ses_sender_address" {
  type = string
}

variable "push_topic_display_name" {
  type    = string
  default = "notification-service-push"
}
