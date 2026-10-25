package com.commure.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProfileValidationConfigurationTest {

	@Test
	void acceptsSingleDatabaseProfile() {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("h2", "cache");
		assertThatCode(() -> ProfileValidationConfiguration.validate(env)).doesNotThrowAnyException();
	}

	@Test
	void rejectsMysqlAndPostgresTogether() {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("mysql", "postgres");
		assertThatThrownBy(() -> ProfileValidationConfiguration.validate(env))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("mysql")
				.hasMessageContaining("postgres");
	}

	@Test
	void rejectsCacheAndHazelcastTogether() {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("h2", "cache", "hazelcast");
		assertThatThrownBy(() -> ProfileValidationConfiguration.validate(env))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cache")
				.hasMessageContaining("hazelcast");
	}

	@Test
	void rejectsMissingDatabaseProfile() {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("cache", "kafka");
		assertThatThrownBy(() -> ProfileValidationConfiguration.validate(env))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("exactly one");
	}

	@Test
	void acceptsDefaultH2WhenNoActiveProfiles() {
		MockEnvironment env = new MockEnvironment();
		env.setDefaultProfiles("h2");
		assertThatCode(() -> ProfileValidationConfiguration.validate(env)).doesNotThrowAnyException();
	}
}
