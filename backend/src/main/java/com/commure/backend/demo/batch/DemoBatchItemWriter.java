package com.commure.backend.demo.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("batch")
public class DemoBatchItemWriter implements ItemWriter<String> {

	private final List<String> written = Collections.synchronizedList(new ArrayList<>());

	@Override
	public void write(Chunk<? extends String> chunk) {
		written.addAll(chunk.getItems());
	}

	public List<String> written() {
		return List.copyOf(written);
	}

	public void clear() {
		written.clear();
	}
}
