package com.commure.backend.demo.rabbit;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("rabbit")
public class DemoRabbitService {

	private final RabbitTemplate rabbitTemplate;
	private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

	public DemoRabbitService(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void send(String message) {
		rabbitTemplate.convertAndSend(DemoRabbitConfig.QUEUE, message);
	}

	@RabbitListener(queues = DemoRabbitConfig.QUEUE)
	public void receive(String message) {
		messages.offer(message);
	}

	public String awaitMessage(long timeoutMs) throws InterruptedException {
		return messages.poll(timeoutMs, TimeUnit.MILLISECONDS);
	}

	public String lastMessage() {
		return messages.peek();
	}
}
