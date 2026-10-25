package com.commure.backend.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@GetMapping("/api/hello")
	public HelloResponse hello() {
		return new HelloResponse("Hello from Commure backend");
	}
}
