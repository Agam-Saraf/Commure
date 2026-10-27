package com.commure.backend.video;

import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

	private final VideoService videoService;

	public VideoController(VideoService videoService) {
		this.videoService = videoService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public VideoResponse upload(@RequestPart(name = "file", required = false) MultipartFile file) {
		return VideoResponse.from(videoService.upload(file));
	}

	@PostMapping("/{id}/transform")
	public VideoResponse transform(@PathVariable UUID id) {
		return VideoResponse.from(videoService.transform(id));
	}

	/**
	 * Returned as a {@link Resource} so Spring serves HTTP Range requests, which Safari needs to play
	 * video. No {@code produces} so error responses can still be rendered as JSON.
	 */
	@GetMapping("/{id}/transformed")
	public ResponseEntity<Resource> transformed(@PathVariable UUID id) {
		return ResponseEntity.ok()
				.contentType(TransformClient.VIDEO_MP4)
				.body(new ByteArrayResource(videoService.getTransformed(id)));
	}
}
