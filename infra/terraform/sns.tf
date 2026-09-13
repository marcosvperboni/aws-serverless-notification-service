resource "aws_sns_topic" "push" {
  name         = "${local.name_prefix}-push"
  display_name = var.push_topic_display_name

  tags = local.common_tags
}
