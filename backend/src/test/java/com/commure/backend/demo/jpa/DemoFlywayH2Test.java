package com.commure.backend.demo.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.commure.backend.TestcontainersConfiguration;

@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:flyway_demo;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({ "h2", "jpa", "flyway" })
class DemoFlywayH2Test {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayMigratesAndJpaWorks() throws Exception {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'DEMO_ITEM' OR table_name = 'demo_item'",
				Integer.class);
		assertThat(count).isNotNull().isGreaterThan(0);

		mockMvc.perform(post("/api/demo/jpa")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"flyway-item\"}"))
				.andExpect(status().isOk());
	}
}
