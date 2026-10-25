package com.commure.backend.demo.websocket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.commure.backend.TestcontainersConfiguration;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({ "h2", "websocket" })
class DemoWebSocketTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void statusEndpointIsReady() throws Exception {
		mockMvc.perform(get("/api/demo/websocket"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ready"))
				.andExpect(jsonPath("$.endpoint").value("/ws"));
	}
}
