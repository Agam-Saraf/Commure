package com.commure.backend.demo.docs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
@Profile("docs")
public class DemoOpenApiConfiguration {

	@Bean
	OpenAPI demoOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Commure Interview Starter")
				.description("Profile-gated OpenAPI surface for the starter REST API.")
				.version("0.0.1"));
	}
}
