package com.commure.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DotEnvEnvironmentPostProcessorTest {

	@Test
	void parsesShellAndPlainFormats() {
		var values = DotEnvEnvironmentPostProcessor.parse(List.of(
				"# comment",
				"",
				"export BASE_URL='https://example.test'",
				"TRANSFORM_SERVICE_TOKEN=\"abc=123\"",
				"PLAIN=value",
				"not-a-pair"));

		assertThat(values)
				.containsEntry("BASE_URL", "https://example.test")
				.containsEntry("TRANSFORM_SERVICE_TOKEN", "abc=123")
				.containsEntry("PLAIN", "value")
				.hasSize(3);
	}
}
