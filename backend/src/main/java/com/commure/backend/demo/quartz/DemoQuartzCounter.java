package com.commure.backend.demo.quartz;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("quartz")
public class DemoQuartzCounter {

	private final AtomicLong executions = new AtomicLong();

	public void increment() {
		executions.incrementAndGet();
	}

	public long count() {
		return executions.get();
	}
}
