package com.commure.backend.video;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Not scoped to {@link VideoController}: multipart size limits are enforced before a handler is
 * resolved, so a scoped advice would never see {@link MaxUploadSizeExceededException}.
 */
@RestControllerAdvice
public class VideoExceptionHandler {

	@ExceptionHandler(VideoApiException.class)
	ResponseEntity<ApiError> handle(VideoApiException ex) {
		var response = ResponseEntity.status(ex.code().status());
		if (ex.retryAfterSeconds() != null) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
		}
		return response.body(ApiError.from(ex));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> handle(MaxUploadSizeExceededException ex) {
		return handle(new VideoApiException(ErrorCode.FILE_TOO_LARGE));
	}
}
