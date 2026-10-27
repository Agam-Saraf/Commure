package com.commure.backend.video;

import java.time.Instant;
import java.util.UUID;

/**
 * @param info probed metadata; null when ffprobe is unavailable or could not read a small clip
 * @param segmentCount parts the transform uses (1 when the clip is sent as-is)
 */
public record StoredVideo(
		UUID id,
		String filename,
		byte[] original,
		VideoInfo info,
		int segmentCount,
		byte[] transformed,
		String requestId,
		Instant createdAt) {

	static StoredVideo of(String filename, byte[] original) {
		return of(filename, original, null, 1);
	}

	static StoredVideo of(String filename, byte[] original, VideoInfo info, int segmentCount) {
		return new StoredVideo(UUID.randomUUID(), filename, original, info, segmentCount, null, null, Instant.now());
	}

	StoredVideo withTransformed(byte[] video, String upstreamRequestId) {
		return new StoredVideo(id, filename, original, info, segmentCount, video, upstreamRequestId, createdAt);
	}

	boolean isTransformed() {
		return transformed != null;
	}
}
