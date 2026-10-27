package com.commure.backend.video;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Third-party transform API settings. Defaults mirror the published limits: 4 MB output,
 * 6 concurrent requests and 12 requests per second.
 */
@ConfigurationProperties("transform")
public record TransformProperties(
		String baseUrl,
		String token,
		@DefaultValue("4000000") long maxOutputBytes,
		@DefaultValue("6") int maxConcurrent,
		@DefaultValue("12") int maxRequestsPerSecond,
		@DefaultValue("2") int maxRetries,
		@DefaultValue("5s") Duration maxRetryWait,
		@DefaultValue("30s") Duration queueTimeout,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("60s") Duration readTimeout) {

	boolean isConfigured() {
		return StringUtils.hasText(baseUrl) && StringUtils.hasText(token);
	}
}
