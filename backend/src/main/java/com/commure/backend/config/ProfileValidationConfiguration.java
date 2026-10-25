package com.commure.backend.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ProfileValidationConfiguration {

	public ProfileValidationConfiguration(Environment environment) {
		validate(environment);
	}

	static void validate(Environment environment) {
		boolean h2 = environment.matchesProfiles("h2");
		boolean mysql = environment.matchesProfiles("mysql");
		boolean postgres = environment.matchesProfiles("postgres");
		boolean cache = environment.matchesProfiles("cache");
		boolean hazelcast = environment.matchesProfiles("hazelcast");

		List<String> dbProfiles = new ArrayList<>();
		if (h2) {
			dbProfiles.add("h2");
		}
		if (mysql) {
			dbProfiles.add("mysql");
		}
		if (postgres) {
			dbProfiles.add("postgres");
		}

		if (mysql && postgres) {
			throw new IllegalStateException(
					"Invalid Spring profiles: 'mysql' and 'postgres' cannot be active together. "
							+ "Choose exactly one database profile. Example: --spring.profiles.active=postgres,jpa");
		}

		if (dbProfiles.size() != 1) {
			throw new IllegalStateException(
					"Invalid Spring profiles: exactly one of [h2, mysql, postgres] must be active, but found "
							+ (dbProfiles.isEmpty() ? "none" : dbProfiles)
							+ ". Active profiles: " + Arrays.toString(environment.getActiveProfiles())
							+ ". Default profiles alone apply only when no profiles are set"
							+ " (spring.profiles.default=h2). "
							+ "Always include a DB profile, e.g. --spring.profiles.active=h2,cache "
							+ "or --spring.profiles.active=postgres,jpa,cache,kafka.");
		}

		if (cache && hazelcast) {
			throw new IllegalStateException(
					"Invalid Spring profiles: 'cache' (Redis) and 'hazelcast' cannot be active together "
							+ "because both define a CacheManager. Choose one. "
							+ "Example: --spring.profiles.active=h2,cache  OR  --spring.profiles.active=h2,hazelcast");
		}
	}
}
