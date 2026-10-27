package com.commure.backend.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VideoStoreTest {

	private final VideoStore store = new VideoStore(2, Long.MAX_VALUE);

	@Test
	void savesAndFindsById() {
		var video = store.save(StoredVideo.of("a.mp4", new byte[] { 1 }));

		assertThat(store.find(video.id())).contains(video);
	}

	@Test
	void missingIdIsEmpty() {
		assertThat(store.find(UUID.randomUUID())).isEmpty();
	}

	@Test
	void evictsOldestUploadWhenCapIsExceeded() {
		var first = store.save(StoredVideo.of("1.mp4", new byte[] { 1 }));
		var second = store.save(StoredVideo.of("2.mp4", new byte[] { 2 }));
		var third = store.save(StoredVideo.of("3.mp4", new byte[] { 3 }));

		assertThat(store.size()).isEqualTo(2);
		assertThat(store.find(first.id())).isEmpty();
		assertThat(store.find(second.id())).isPresent();
		assertThat(store.find(third.id())).isPresent();
	}

	@Test
	void evictsOldestUntilTotalBytesFitIncludingTransformedResults() {
		var bytesCapped = new VideoStore(10, 100);
		var first = bytesCapped.save(StoredVideo.of("1.mp4", new byte[30]));
		bytesCapped.update(first.id(), v -> v.withTransformed(new byte[30], null));
		var second = bytesCapped.save(StoredVideo.of("2.mp4", new byte[30]));
		var third = bytesCapped.save(StoredVideo.of("3.mp4", new byte[30]));

		assertThat(bytesCapped.find(first.id())).isEmpty();
		assertThat(bytesCapped.find(second.id())).isPresent();
		assertThat(bytesCapped.find(third.id())).isPresent();
	}

	@Test
	void alwaysKeepsTheNewestUploadEvenIfItAloneExceedsTheByteCap() {
		var bytesCapped = new VideoStore(10, 10);
		bytesCapped.save(StoredVideo.of("small.mp4", new byte[5]));
		var big = bytesCapped.save(StoredVideo.of("big.mp4", new byte[50]));

		assertThat(bytesCapped.size()).isEqualTo(1);
		assertThat(bytesCapped.find(big.id())).isPresent();
	}

	@Test
	void updateChangesExistingEntryWithoutReorderingEviction() {
		var first = store.save(StoredVideo.of("1.mp4", new byte[] { 1 }));
		var second = store.save(StoredVideo.of("2.mp4", new byte[] { 2 }));

		var updated = store.update(first.id(), v -> v.withTransformed(new byte[] { 9 }, "req-1"));
		store.save(StoredVideo.of("3.mp4", new byte[] { 3 }));

		assertThat(updated).hasValueSatisfying(v -> assertThat(v.requestId()).isEqualTo("req-1"));
		assertThat(store.find(first.id())).isEmpty();
		assertThat(store.find(second.id())).isPresent();
	}

	@Test
	void updateDoesNotResurrectEvictedVideo() {
		var id = UUID.randomUUID();

		assertThat(store.update(id, v -> v)).isEmpty();
		assertThat(store.find(id)).isEmpty();
	}
}
