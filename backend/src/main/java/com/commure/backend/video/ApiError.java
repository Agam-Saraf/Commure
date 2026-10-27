package com.commure.backend.video;

public record ApiError(String code, String message, String requestId, Long retryAfterSeconds) {

	static ApiError from(VideoApiException ex) {
		return new ApiError(ex.code().name(), ex.getMessage(), ex.requestId(), ex.retryAfterSeconds());
	}
}
