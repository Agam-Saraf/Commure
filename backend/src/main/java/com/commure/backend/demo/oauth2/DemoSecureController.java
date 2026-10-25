package com.commure.backend.demo.oauth2;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secure")
@Profile("oauth2")
public class DemoSecureController {

	@GetMapping("/me")
	public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("subject", jwt.getSubject());
		body.put("preferredUsername", jwt.getClaimAsString("preferred_username"));
		body.put("email", jwt.getClaimAsString("email"));
		body.put("issuer", jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
		return body;
	}
}
