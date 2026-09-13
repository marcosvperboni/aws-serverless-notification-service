package com.marcosperboni.notification.config;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

public class AwsClients {

	private static final String ENDPOINT_OVERRIDE_ENV = "AWS_ENDPOINT_URL";
	private static final String REGION_ENV = "AWS_REGION";
	private static final String DEFAULT_REGION = "us-east-1";

	private final SdkHttpClient httpClient = UrlConnectionHttpClient.create();

	public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
		return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient()).build();
	}

	public DynamoDbClient dynamoDbClient() {
		return configure(DynamoDbClient.builder()).build();
	}

	public SqsClient sqsClient() {
		return configure(SqsClient.builder()).build();
	}

	public SnsClient snsClient() {
		return configure(SnsClient.builder()).build();
	}

	public SesClient sesClient() {
		return configure(SesClient.builder()).build();
	}

	private <B extends AwsClientBuilder<B, ?> & SdkSyncClientBuilder<B, ?>> B configure(B builder) {
		builder.httpClient(httpClient);
		builder.region(Region.of(System.getenv().getOrDefault(REGION_ENV, DEFAULT_REGION)));
		String endpointOverride = System.getenv(ENDPOINT_OVERRIDE_ENV);
		if (endpointOverride != null && !endpointOverride.isBlank()) {
			builder.endpointOverride(URI.create(endpointOverride));
			builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
		} else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}
		return builder;
	}
}
