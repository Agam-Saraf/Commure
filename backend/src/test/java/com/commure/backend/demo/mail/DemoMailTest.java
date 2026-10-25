package com.commure.backend.demo.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:mail_demo;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "mail" })
class DemoMailTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JavaMailSender mailSender;

	@Test
	void sendMailInvokesJavaMailSender() throws Exception {
		mockMvc.perform(post("/api/demo/mail")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"to\":\"a@b.com\",\"subject\":\"hi\",\"text\":\"body\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("sent"))
				.andExpect(jsonPath("$.to").value("a@b.com"));

		verify(mailSender).send(any(SimpleMailMessage.class));
	}
}
