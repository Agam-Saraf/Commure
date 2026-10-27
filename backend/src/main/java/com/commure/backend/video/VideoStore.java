package com.commure.backend.video;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory, bounded store. Each new upload evicts the oldest entries until both the entry cap and
 * the byte cap (originals plus grayscale results) hold; the newest upload is always kept.
 */
@Component
public class VideoStore {

	private final Map<UUID, StoredVideo> videos = new LinkedHashMap<>();
	private final int maxEntries;
	private final long maxBytes;

	public VideoStore(@Value("${video.store.max-entries:20}") int maxEntries,
			@Value("${video.store.max-bytes:500000000}") long maxBytes) {
		this.maxEntries = maxEntries;
		this.maxBytes = maxBytes;
	}

	public synchronized StoredVideo save(StoredVideo video) {
		videos.put(video.id(), video);
		var oldestFirst = videos.values().iterator();
		while (videos.size() > 1 && (videos.size() > maxEntries || totalBytes() > maxBytes)) {
			oldestFirst.next();
			oldestFirst.remove();
		}
		return video;
	}

	public synchronized Optional<StoredVideo> find(UUID id) {
		return Optional.ofNullable(videos.get(id));
	}

	/** Updates an existing entry only; an evicted video is not re-added. */
	public synchronized Optional<StoredVideo> update(UUID id, UnaryOperator<StoredVideo> change) {
		return Optional.ofNullable(videos.computeIfPresent(id, (key, current) -> change.apply(current)));
	}

	public synchronized int size() {
		return videos.size();
	}

	private long totalBytes() {
		return videos.values().stream()
				.mapToLong(v -> v.original().length + (v.isTransformed() ? v.transformed().length : 0))
				.sum();
	}
}
