package com.commure.backend.demo.metrics;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/api/demo/metrics")
@Profile("metrics")
public class DemoMetricsController {

	private final Counter hits;

	public DemoMetricsController(MeterRegistry registry) {
		this.hits = Counter.builder("commure.demo.hits")
				.description("Demo counter for metrics profile verification")
				.register(registry);
	}

	@GetMapping
	public Map<String, Object> hit() {
		hits.increment();
		return Map.of("hits", (long) hits.count());
	}
}
