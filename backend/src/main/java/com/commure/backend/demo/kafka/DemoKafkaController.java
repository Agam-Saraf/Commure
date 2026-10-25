package com.commure.backend.demo.kafka;

import java.time.Duration;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/kafka")
@Profile("kafka")
public class DemoKafkaController {

	private final DemoKafkaService service;

	public DemoKafkaController(DemoKafkaService service) {
		this.service = service;
	}

	@PostMapping
	public Map<String, String> publish(@RequestBody Map<String, String> body) throws InterruptedException {
		String message = body.getOrDefault("message", "hello-kafka");
		service.send(message);
		String received = service.awaitMessage(Duration.ofSeconds(10));
		return Map.of("sent", message, "received", received == null ? "" : received);
	}

	@GetMapping("/last")
	public Map<String, String> last() {
		String value = service.lastMessage();
		return Map.of("last", value == null ? "" : value);
	}
}
