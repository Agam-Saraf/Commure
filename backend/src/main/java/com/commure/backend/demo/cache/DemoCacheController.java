package com.commure.backend.demo.cache;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/cache")
@Profile("cache")
public class DemoCacheController {

	private final DemoCacheService service;

	public DemoCacheController(DemoCacheService service) {
		this.service = service;
	}

	@GetMapping("/{key}")
	public Map<String, Object> get(@PathVariable String key) {
		String value = service.load(key);
		return Map.of("key", key, "value", value, "misses", service.missCount());
	}
}
