package com.commure.backend.demo.mongo;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/mongo")
@Profile("mongo")
public class DemoMongoController {

	private final DemoDocumentRepository repository;

	public DemoMongoController(DemoDocumentRepository repository) {
		this.repository = repository;
	}

	@PostMapping
	public DemoDocument create(@RequestBody Map<String, String> body) {
		return repository.save(new DemoDocument(body.getOrDefault("value", "demo")));
	}

	@GetMapping
	public List<DemoDocument> list() {
		return repository.findAll();
	}

	@PutMapping("/{id}")
	public DemoDocument update(@PathVariable String id, @RequestBody Map<String, String> body) {
		DemoDocument doc = repository.findById(id).orElseThrow();
		doc.setValue(body.getOrDefault("value", doc.getValue()));
		return repository.save(doc);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		repository.deleteById(id);
	}
}
