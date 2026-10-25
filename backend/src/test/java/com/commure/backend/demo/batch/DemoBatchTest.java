package com.commure.backend.demo.batch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:batch_demo;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "batch" })
class DemoBatchTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void runJobUppercasesItems() throws Exception {
		mockMvc.perform(post("/api/demo/batch/run"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.written[0]").value("ALPHA"))
				.andExpect(jsonPath("$.written[1]").value("BETA"))
				.andExpect(jsonPath("$.written[2]").value("GAMMA"));
	}
}
