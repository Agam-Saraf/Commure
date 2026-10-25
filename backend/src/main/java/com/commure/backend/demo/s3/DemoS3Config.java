package com.commure.backend.demo.s3;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@Profile("s3")
@EnableConfigurationProperties(DemoS3Properties.class)
public class DemoS3Config {

	@Bean(destroyMethod = "close")
	S3Client demoS3Client(DemoS3Properties properties) {
		return S3Client.builder()
				.endpointOverride(URI.create(properties.getEndpoint()))
				.region(Region.of(properties.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
	}
}
