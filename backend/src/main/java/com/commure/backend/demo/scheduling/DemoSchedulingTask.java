package com.commure.backend.demo.scheduling;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("scheduling")
public class DemoSchedulingTask {

	private final AtomicLong ticks = new AtomicLong();

	@Value("${commure.demo.scheduling.fixed-rate-ms:1000}")
	private long fixedRateMs;

	@Scheduled(fixedRateString = "${commure.demo.scheduling.fixed-rate-ms:1000}")
	public void tick() {
		ticks.incrementAndGet();
	}

	public long tickCount() {
		return ticks.get();
	}

	public long fixedRateMs() {
		return fixedRateMs;
	}
}
