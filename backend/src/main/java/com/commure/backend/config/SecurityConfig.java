package com.commure.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("!oauth2")
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.ignoringRequestMatchers(
						"/api/**",
						"/graphql",
						"/graphiql",
						"/graphiql/**",
						"/ws/**"))
				.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/hello").permitAll()
				.requestMatchers("/api/demo/**").permitAll()
				.requestMatchers("/api/videos/**").permitAll()
				.requestMatchers("/graphql", "/graphiql", "/graphiql/**").permitAll()
				.requestMatchers("/ws/**").permitAll()
				.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers(
						"/actuator/health",
						"/actuator/health/**",
						"/actuator/prometheus",
						"/actuator/metrics",
						"/actuator/metrics/**")
				.permitAll()
				.anyRequest().authenticated());
		return http.build();
	}
}
