package com.commure.backend.demo.jpa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.commure.backend.TestcontainersConfiguration;

@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:jpa_demo;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({ "h2", "jpa" })
class DemoJpaH2Test {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createAndListItems() throws Exception {
		mockMvc.perform(post("/api/demo/jpa")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"alpha\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("alpha"))
				.andExpect(jsonPath("$.id").isNumber());

		mockMvc.perform(get("/api/demo/jpa"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'alpha')]").isArray())
				.andExpect(jsonPath("$[?(@.name == 'alpha')]").isNotEmpty());
	}
}
