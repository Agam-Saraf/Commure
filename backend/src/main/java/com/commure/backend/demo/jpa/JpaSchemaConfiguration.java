package com.commure.backend.demo.jpa;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Resolves ddl-auto without relying on profile property file order.
 * jpa without flyway → update; jpa with flyway → validate.
 */
@Configuration
@Profile("jpa")
public class JpaSchemaConfiguration {

	@Bean
	HibernatePropertiesCustomizer demoJpaHibernatePropertiesCustomizer(Environment environment) {
		return props -> {
			if (environment.matchesProfiles("flyway")) {
				props.put("hibernate.hbm2ddl.auto", "validate");
			}
			else {
				props.put("hibernate.hbm2ddl.auto", "update");
			}
		};
	}
}
