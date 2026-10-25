package com.commure.backend.demo.rabbit;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("rabbit")
public class DemoRabbitConfig {

	public static final String QUEUE = "commure.demo.queue";

	@Bean
	Queue demoQueue() {
		return new Queue(QUEUE, true);
	}
}
