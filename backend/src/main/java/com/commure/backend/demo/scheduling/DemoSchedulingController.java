package com.commure.backend.demo.scheduling;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/scheduling")
@Profile("scheduling")
public class DemoSchedulingController {

	private final DemoSchedulingTask task;

	public DemoSchedulingController(DemoSchedulingTask task) {
		this.task = task;
	}

	@GetMapping
	public Map<String, Object> status() {
		return Map.of(
				"ticks", task.tickCount(),
				"fixedRateMs", task.fixedRateMs());
	}
}
