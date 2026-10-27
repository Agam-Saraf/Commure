package com.commure.backend.video;

public record VideoInfo(double durationSeconds, int width, int height, double fps) {

	long estimatedFrames() {
		return Math.round(durationSeconds * fps);
	}
}
