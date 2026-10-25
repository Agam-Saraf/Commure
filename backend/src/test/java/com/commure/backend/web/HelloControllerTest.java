package com.commure.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.commure.backend.config.SecurityConfig;

@WebMvcTest(HelloController.class)
@Import(SecurityConfig.class)
class HelloControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void helloIsAccessibleAnonymously() throws Exception {
		mockMvc.perform(get("/api/hello"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Hello from Commure backend"));
	}

	@Test
	void demoPathsArePermittedAnonymouslyEvenWithoutDemoControllers() throws Exception {
		mockMvc.perform(get("/api/demo/missing"))
				.andExpect(status().isNotFound());
	}

	@Test
	void unrelatedEndpointIsNotAccessibleAnonymously() throws Exception {
		mockMvc.perform(get("/api/secure"))
				.andExpect(result -> {
					int statusCode = result.getResponse().getStatus();
					if (statusCode != 401 && statusCode != 403) {
						throw new AssertionError(
								"Expected 401 or 403 for protected endpoint but was " + statusCode);
					}
				});
	}
}
