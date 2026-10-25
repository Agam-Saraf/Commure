package com.commure.backend.demo.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

import com.commure.backend.TestcontainersConfiguration;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureGraphQlTester
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({ "h2", "graphql" })
class DemoGraphqlTest {

	@Autowired
	private GraphQlTester graphQlTester;

	@Test
	void demoMessageQuery() {
		graphQlTester.document("{ demoMessage }")
				.execute()
				.path("demoMessage")
				.entity(String.class)
				.isEqualTo("Hello from GraphQL demo");
	}
}
