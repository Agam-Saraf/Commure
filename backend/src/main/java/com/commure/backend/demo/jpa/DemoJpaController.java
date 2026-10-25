package com.commure.backend.demo.jpa;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/jpa")
@Profile("jpa")
public class DemoJpaController {

	private final DemoItemRepository repository;

	public DemoJpaController(DemoItemRepository repository) {
		this.repository = repository;
	}

	@PostMapping
	public DemoItem create(@RequestBody Map<String, String> body) {
		String name = body.getOrDefault("name", "demo");
		return repository.save(new DemoItem(name));
	}

	@GetMapping
	public List<DemoItem> list() {
		return repository.findAll();
	}
}
