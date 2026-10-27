package com.commure.backend.video;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Upload limits and the per-request limits of the transform API. Videos that exceed the clip
 * limits are split into parts of at most {@code segmentSeconds}; 9.9s keeps a frame-aligned cut
 * within the API's 10s limit for any frame rate of 10 fps or more (300 frames at 29.97 fps is
 * already 10.01s).
 */
@ConfigurationProperties("video")
public record VideoProperties(
		@DefaultValue("50000000") long maxUploadBytes,
		@DefaultValue("120") double maxDurationSeconds,
		@DefaultValue("3000000") long maxClipBytes,
		@DefaultValue("10") double maxClipSeconds,
		@DefaultValue("600") int maxClipFrames,
		@DefaultValue("1920") int maxClipLongSide,
		@DefaultValue("1080") int maxClipShortSide,
		@DefaultValue("9.9") double segmentSeconds,
		@DefaultValue Ffmpeg ffmpeg) {

	/** {@code maxBitrateKbps} with a 2x VBV buffer keeps a 10s part near 2.25 MB, under the 3 MB limit. */
	public record Ffmpeg(
			@DefaultValue("ffmpeg") String ffmpegPath,
			@DefaultValue("ffprobe") String ffprobePath,
			@DefaultValue("120s") Duration timeout,
			@DefaultValue("1500") int maxBitrateKbps) {
	}
}
