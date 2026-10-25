package com.commure.backend.demo.mail;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/mail")
@Profile("mail")
public class DemoMailController {

	private final JavaMailSender mailSender;

	public DemoMailController(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	@PostMapping
	public Map<String, String> send(@RequestBody Map<String, String> body) {
		String to = body.getOrDefault("to", "demo@example.com");
		String subject = body.getOrDefault("subject", "Commure demo");
		String text = body.getOrDefault("text", "Hello from Commure mail demo");

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom("noreply@commure.local");
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		mailSender.send(message);

		return Map.of("status", "sent", "to", to, "subject", subject);
	}
}
