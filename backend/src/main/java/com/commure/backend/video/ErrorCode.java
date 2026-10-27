package com.commure.backend.video;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	EMPTY_FILE(HttpStatus.BAD_REQUEST, "Please choose a video file."),
	UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only MP4 videos are supported."),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Videos must be 50 MB or smaller."),
	VIDEO_TOO_LONG(HttpStatus.UNPROCESSABLE_CONTENT, "Videos must be 2 minutes or shorter."),
	UNREADABLE_VIDEO(HttpStatus.UNPROCESSABLE_CONTENT, "We couldn't read this video. Please upload a valid MP4."),
	PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
			"We couldn't split or reassemble this video. Please try another file."),
	PROCESSOR_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
			"Long videos can't be processed on this server right now. Please upload a clip of 10 seconds and 3 MB or less."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "That video is no longer available. Please upload it again."),
	NOT_TRANSFORMED(HttpStatus.NOT_FOUND, "This video has not been converted yet."),

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "The video could not be processed. Please try uploading it again."),
	SERVICE_AUTH_FAILED(HttpStatus.BAD_GATEWAY,
			"The transformation service rejected our credentials. Please contact support."),
	SESSION_NOT_LIVE(HttpStatus.SERVICE_UNAVAILABLE,
			"The transformation service is not live right now. Please try again later."),
	LIMIT_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE,
			"This clip exceeds the limits: 3 MB, 10 seconds, 1920x1080, 600 frames."),
	UNSUPPORTED_VIDEO(HttpStatus.UNPROCESSABLE_CONTENT, "This video is not supported. Please upload an H.264 MP4."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "The transformation service is busy. Please try again shortly."),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
			"The transformation service is temporarily unavailable. Please try again shortly."),
	OUTPUT_TOO_LARGE(HttpStatus.BAD_GATEWAY, "The converted video exceeded the 4 MB limit. Try a shorter clip."),
	TIMEOUT(HttpStatus.GATEWAY_TIMEOUT,
			"The transformation service could not be reached or took too long to respond. Please try again."),
	NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Video transformation is not configured on the server."),
	UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY,
			"The transformation service returned an unexpected error. Please try again.");

	private final HttpStatus status;
	private final String userMessage;

	ErrorCode(HttpStatus status, String userMessage) {
		this.status = status;
		this.userMessage = userMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String userMessage() {
		return userMessage;
	}
}
