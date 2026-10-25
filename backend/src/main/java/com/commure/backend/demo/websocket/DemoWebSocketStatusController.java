package com.commure.backend.demo.websocket;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/websocket")
@Profile("websocket")
public class DemoWebSocketStatusController {

	@GetMapping
	public Map<String, String> status() {
		return Map.of(
				"status", "ready",
				"endpoint", "/ws",
				"subscribe", "/topic/demo",
				"send", "/app/demo");
	}
}
