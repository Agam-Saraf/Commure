package com.commure.backend.demo.batch;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("batch")
public class DemoBatchItemProcessor implements ItemProcessor<String, String> {

	@Override
	public String process(String item) {
		return item == null ? null : item.toUpperCase();
	}
}
