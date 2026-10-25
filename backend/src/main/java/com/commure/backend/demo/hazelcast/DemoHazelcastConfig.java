package com.commure.backend.demo.hazelcast;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;

@Configuration
@EnableCaching
@Profile("hazelcast")
public class DemoHazelcastConfig {

	public static final String MAP_NAME = "demoHazelcastCache";

	@Bean
	Config hazelcastConfig() {
		EvictionConfig eviction = new EvictionConfig()
				.setEvictionPolicy(EvictionPolicy.LRU)
				.setMaxSizePolicy(MaxSizePolicy.PER_NODE)
				.setSize(10);

		MapConfig mapConfig = new MapConfig(MAP_NAME)
				.setEvictionConfig(eviction)
				.setTimeToLiveSeconds(5);

		return new Config("commure-demo")
				.setClusterName("commure-demo")
				.setProperty("hazelcast.logging.type", "slf4j")
				.addMapConfig(mapConfig);
	}

	@Bean(destroyMethod = "shutdown")
	HazelcastInstance hazelcastInstance(Config hazelcastConfig) {
		return Hazelcast.newHazelcastInstance(hazelcastConfig);
	}

	@Bean
	CacheManager cacheManager(HazelcastInstance hazelcastInstance) {
		return new HazelcastCacheManager(hazelcastInstance);
	}
}
