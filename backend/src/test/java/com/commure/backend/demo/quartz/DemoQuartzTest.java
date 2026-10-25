package com.commure.backend.demo.quartz;

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
		"spring.datasource.url=jdbc:h2:mem:quartz_demo;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "quartz" })
class DemoQuartzTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DemoQuartzCounter counter;

	@Test
	void quartzJobExecutes() throws Exception {
		long deadline = System.currentTimeMillis() + 5000;
		while (counter.count() == 0 && System.currentTimeMillis() < deadline) {
			Thread.sleep(100);
		}
		assertThat(counter.count()).isGreaterThan(0);

		mockMvc.perform(get("/api/demo/quartz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.executions").isNumber());
	}
}
