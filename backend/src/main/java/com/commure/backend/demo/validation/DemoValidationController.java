package com.commure.backend.demo.validation;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/demo/validation")
@Profile("validation")
@Validated
public class DemoValidationController {

	@PostMapping
	public Map<String, String> accept(@Valid @RequestBody DemoValidationRequest request) {
		return Map.of(
				"status", "accepted",
				"name", request.getName(),
				"email", request.getEmail());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> onValidationError(MethodArgumentNotValidException ex) {
		var errors = ex.getBindingResult().getFieldErrors().stream()
				.map(err -> Map.of("field", err.getField(), "message", String.valueOf(err.getDefaultMessage())))
				.toList();
		return Map.of("status", "invalid", "errors", errors);
	}
}
