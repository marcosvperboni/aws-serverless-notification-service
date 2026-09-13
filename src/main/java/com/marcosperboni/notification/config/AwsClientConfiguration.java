package com.marcosperboni.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsClientConfiguration {

	@Bean
	public AwsClients awsClients() {
		return new AwsClients();
	}

	@Bean
	public DynamoDbEnhancedClient dynamoDbEnhancedClient(AwsClients awsClients) {
		return awsClients.dynamoDbEnhancedClient();
	}

	@Bean
	public SqsClient sqsClient(AwsClients awsClients) {
		return awsClients.sqsClient();
	}

	@Bean
	public SnsClient snsClient(AwsClients awsClients) {
		return awsClients.snsClient();
	}

	@Bean
	public SesClient sesClient(AwsClients awsClients) {
		return awsClients.sesClient();
	}
}
