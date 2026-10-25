package com.commure.backend.demo.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
		"spring.datasource.url=jdbc:h2:mem:sched_demo;DB_CLOSE_DELAY=-1",
		"commure.demo.scheduling.fixed-rate-ms=50"
})
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "scheduling" })
class DemoSchedulingTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DemoSchedulingTask task;

	@Test
	void scheduledTaskIncrementsTicks() throws Exception {
		Thread.sleep(200);
		assertThat(task.tickCount()).isGreaterThan(0);

		mockMvc.perform(get("/api/demo/scheduling"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ticks").isNumber())
				.andExpect(jsonPath("$.fixedRateMs").value(50));
	}
}
