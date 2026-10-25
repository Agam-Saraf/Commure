package com.commure.backend.demo.s3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:s3_demo;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "s3" })
class DemoS3Test {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DemoS3Service demoS3Service;

	@Test
	void putAndListViaController() throws Exception {
		when(demoS3Service.bucket()).thenReturn("commure-demo");
		doNothing().when(demoS3Service).putText(anyString(), anyString());
		when(demoS3Service.listKeys()).thenReturn(List.of("notes.txt"));
		when(demoS3Service.getText("notes.txt")).thenReturn("hello-minio");

		mockMvc.perform(put("/api/demo/s3/notes.txt")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"hello-minio\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.key").value("notes.txt"));

		mockMvc.perform(get("/api/demo/s3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0]").value("notes.txt"));

		mockMvc.perform(get("/api/demo/s3/notes.txt"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("hello-minio"));
	}
}
