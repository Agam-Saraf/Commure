package com.commure.backend.video;

import java.util.UUID;

/** {@code requestId} is a comma-separated list of upstream request ids when the video was split. */
public record VideoResponse(
		UUID id,
		String filename,
		long sizeBytes,
		Double durationSeconds,
		int segmentCount,
		boolean transformed,
		Long transformedSizeBytes,
		String requestId) {

	static VideoResponse from(StoredVideo video) {
		return new VideoResponse(
				video.id(),
				video.filename(),
				video.original().length,
				video.info() != null ? video.info().durationSeconds() : null,
				video.segmentCount(),
				video.isTransformed(),
				video.isTransformed() ? (long) video.transformed().length : null,
				video.requestId());
	}
}
