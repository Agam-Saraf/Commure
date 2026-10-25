package com.commure.backend.demo.hazelcast;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("hazelcast")
public class DemoHazelcastService {

	private final AtomicInteger loads = new AtomicInteger();

	@Cacheable(cacheNames = DemoHazelcastConfig.MAP_NAME, key = "#key")
	public String load(String key) {
		loads.incrementAndGet();
		return "hz-" + key;
	}

	public int loadCount() {
		return loads.get();
	}
}
