package com.commure.backend.video;

public class VideoApiException extends RuntimeException {

	private final ErrorCode code;
	private final String requestId;
	private final Long retryAfterSeconds;

	public VideoApiException(ErrorCode code) {
		this(code, null, null);
	}

	public VideoApiException(ErrorCode code, String requestId, Long retryAfterSeconds) {
		this(code, code.userMessage(), requestId, retryAfterSeconds);
	}

	private VideoApiException(ErrorCode code, String message, String requestId, Long retryAfterSeconds) {
		super(message);
		this.code = code;
		this.requestId = requestId;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/** Same failure, with a user-facing prefix such as "Part 2 of 5". */
	VideoApiException withContext(String context) {
		return new VideoApiException(code, context + ": " + getMessage(), requestId, retryAfterSeconds);
	}

	public ErrorCode code() {
		return code;
	}

	public String requestId() {
		return requestId;
	}

	public Long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
