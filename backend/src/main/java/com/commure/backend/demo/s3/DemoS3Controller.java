package com.commure.backend.demo.s3;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/s3")
@Profile("s3")
public class DemoS3Controller {

	private final DemoS3Service service;

	public DemoS3Controller(DemoS3Service service) {
		this.service = service;
	}

	@PutMapping("/{key}")
	public Map<String, Object> put(@PathVariable String key, @RequestBody Map<String, String> body) {
		String content = body.getOrDefault("content", "hello-minio");
		service.putText(key, content);
		return Map.of("bucket", service.bucket(), "key", key, "content", content);
	}

	@GetMapping("/{key}")
	public Map<String, Object> get(@PathVariable String key) {
		return Map.of("bucket", service.bucket(), "key", key, "content", service.getText(key));
	}

	@GetMapping
	public Map<String, Object> list() {
		return Map.of("bucket", service.bucket(), "keys", service.listKeys());
	}
}
