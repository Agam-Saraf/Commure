package com.commure.backend.demo.quartz;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/quartz")
@Profile("quartz")
public class DemoQuartzController {

	private final DemoQuartzCounter counter;

	public DemoQuartzController(DemoQuartzCounter counter) {
		this.counter = counter;
	}

	@GetMapping
	public Map<String, Object> status() {
		return Map.of("executions", counter.count());
	}
}
