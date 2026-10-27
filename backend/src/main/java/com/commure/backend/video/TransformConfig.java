package com.commure.backend.video;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ TransformProperties.class, VideoProperties.class })
class TransformConfig {

	@Bean
	TransformClient transformClient(TransformProperties properties) {
		var httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return new TransformClient(properties, RestClient.builder().requestFactory(requestFactory));
	}
}
