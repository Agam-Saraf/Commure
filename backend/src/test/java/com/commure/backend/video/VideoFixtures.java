package com.commure.backend.video;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.mock.web.MockMultipartFile;

final class VideoFixtures {

	private VideoFixtures() {
	}

	/** Synthetic bytes with a valid MP4 {@code ftyp} header; not decodable video. */
	static byte[] mp4Bytes(int size) {
		byte[] bytes = new byte[size];
		byte[] header = "\0\0\0\u0018ftypisom".getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(header, 0, bytes, 0, Math.min(header.length, size));
		return bytes;
	}

	static MockMultipartFile mp4Part(int size) {
		return mp4Part(mp4Bytes(size));
	}

	static MockMultipartFile mp4Part(byte[] bytes) {
		return new MockMultipartFile("file", "clip.mp4", "video/mp4", bytes);
	}

	static VideoProperties properties() {
		return properties(50_000_000);
	}

	static VideoProperties properties(long maxUploadBytes) {
		return new VideoProperties(maxUploadBytes, 120, 3_000_000, 10, 600, 1920, 1080, 9.9,
				new VideoProperties.Ffmpeg("ffmpeg", "ffprobe", Duration.ofSeconds(120), 1500));
	}
}
