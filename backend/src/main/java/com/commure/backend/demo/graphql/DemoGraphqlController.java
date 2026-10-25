package com.commure.backend.demo.graphql;

import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@Profile("graphql")
public class DemoGraphqlController {

	@QueryMapping
	public String demoMessage() {
		return "Hello from GraphQL demo";
	}
}
