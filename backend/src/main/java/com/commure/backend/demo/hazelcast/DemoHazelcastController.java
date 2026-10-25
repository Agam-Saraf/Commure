package com.commure.backend.demo.hazelcast;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/hazelcast")
@Profile("hazelcast")
public class DemoHazelcastController {

	private final DemoHazelcastService service;

	public DemoHazelcastController(DemoHazelcastService service) {
		this.service = service;
	}

	@GetMapping("/{key}")
	public Map<String, Object> get(@PathVariable String key) {
		String value = service.load(key);
		return Map.of("key", key, "value", value, "loads", service.loadCount());
	}
}
