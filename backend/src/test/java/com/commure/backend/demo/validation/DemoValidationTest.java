package com.commure.backend.demo.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:validation_demo;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "validation" })
class DemoValidationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void acceptsValidPayload() throws Exception {
		mockMvc.perform(post("/api/demo/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ada\",\"email\":\"ada@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("accepted"));
	}

	@Test
	void rejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/api/demo/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value("invalid"))
				.andExpect(jsonPath("$.errors").isArray());
	}
}
