package com.commure.backend.demo.websocket;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@Profile("websocket")
public class DemoWebSocketController {

	@MessageMapping("/demo")
	@SendTo("/topic/demo")
	public Map<String, String> echo(Map<String, String> body) {
		String message = body == null ? "hello" : body.getOrDefault("message", "hello");
		return Map.of("echo", message);
	}
}
