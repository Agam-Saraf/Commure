package com.commure.backend.demo.oauth2;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:oauth2_demo;DB_CLOSE_DELAY=-1",
		"spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
@AutoConfigureMockMvc
@Import(DemoOAuth2Test.TestJwtConfig.class)
@ActiveProfiles({ "h2", "oauth2" })
class DemoOAuth2Test {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void secureMeRequiresJwt() throws Exception {
		mockMvc.perform(get("/api/secure/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void secureMeReturnsClaims() throws Exception {
		mockMvc.perform(get("/api/secure/me").with(jwt().jwt(builder -> builder
				.subject("demo-sub")
				.claim("preferred_username", "demo")
				.claim("email", "demo@commure.local"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subject").value("demo-sub"))
				.andExpect(jsonPath("$.preferredUsername").value("demo"));
	}

	@TestConfiguration
	static class TestJwtConfig {

		@Bean
		@Primary
		JwtDecoder jwtDecoder() {
			return token -> Jwt.withTokenValue(token)
					.header("alg", "none")
					.subject("demo-sub")
					.claim("preferred_username", "demo")
					.issuedAt(Instant.now())
					.expiresAt(Instant.now().plusSeconds(300))
					.build();
		}
	}
}
