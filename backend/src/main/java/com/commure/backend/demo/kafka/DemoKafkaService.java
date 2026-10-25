package com.commure.backend.demo.kafka;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("kafka")
public class DemoKafkaService {

	public static final String TOPIC = "commure.demo.topic";

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final AtomicReference<String> lastMessage = new AtomicReference<>();
	private final CountDownLatch latch = new CountDownLatch(1);

	public DemoKafkaService(KafkaTemplate<String, String> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	public void send(String message) {
		kafkaTemplate.send(TOPIC, message);
	}

	@KafkaListener(topics = TOPIC, groupId = "commure-demo")
	public void receive(String message) {
		lastMessage.set(message);
		latch.countDown();
	}

	public String awaitMessage(Duration timeout) throws InterruptedException {
		latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
		return lastMessage.get();
	}

	public String lastMessage() {
		return lastMessage.get();
	}
}
