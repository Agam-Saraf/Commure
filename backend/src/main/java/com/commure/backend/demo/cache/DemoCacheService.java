package com.commure.backend.demo.cache;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("cache")
public class DemoCacheService {

	private final AtomicInteger misses = new AtomicInteger();

	@Cacheable(cacheNames = "demoCache", key = "#key")
	public String load(String key) {
		misses.incrementAndGet();
		return "value-for-" + key;
	}

	public int missCount() {
		return misses.get();
	}
}
