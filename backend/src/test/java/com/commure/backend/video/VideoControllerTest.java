package com.commure.backend.video;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.commure.backend.config.SecurityConfig;

@WebMvcTest(VideoController.class)
@Import({ SecurityConfig.class, VideoExceptionHandler.class })
class VideoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VideoService videoService;

	@Test
	void uploadReturnsCreatedWithMetadata() throws Exception {
		var stored = StoredVideo.of("clip.mp4", VideoFixtures.mp4Bytes(2048));
		given(videoService.upload(any())).willReturn(stored);

		mockMvc.perform(multipart("/api/videos").file(VideoFixtures.mp4Part(2048)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(stored.id().toString()))
				.andExpect(jsonPath("$.filename").value("clip.mp4"))
				.andExpect(jsonPath("$.sizeBytes").value(2048))
				.andExpect(jsonPath("$.transformed").value(false));
	}

	@Test
	void emptyUploadIsBadRequest() throws Exception {
		assertUploadError(ErrorCode.EMPTY_FILE, 400);
	}

	@Test
	void unsupportedTypeIs415() throws Exception {
		assertUploadError(ErrorCode.UNSUPPORTED_TYPE, 415);
	}

	@Test
	void oversizedUploadIs413() throws Exception {
		assertUploadError(ErrorCode.FILE_TOO_LARGE, 413);
	}

	@Test
	void transformReturnsTransformedMetadata() throws Exception {
		var stored = StoredVideo.of("clip.mp4", VideoFixtures.mp4Bytes(2048))
				.withTransformed(VideoFixtures.mp4Bytes(1024), "req-42");
		given(videoService.transform(stored.id())).willReturn(stored);

		mockMvc.perform(post("/api/videos/{id}/transform", stored.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.transformed").value(true))
				.andExpect(jsonPath("$.transformedSizeBytes").value(1024))
				.andExpect(jsonPath("$.requestId").value("req-42"));
	}

	@Test
	void transformErrorCarriesRetryAfterHeaderAndRequestId() throws Exception {
		var id = UUID.randomUUID();
		given(videoService.transform(id)).willThrow(new VideoApiException(ErrorCode.RATE_LIMITED, "req-429", 7L));

		mockMvc.perform(post("/api/videos/{id}/transform", id))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "7"))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.RATE_LIMITED.userMessage()))
				.andExpect(jsonPath("$.requestId").value("req-429"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(7));
	}

	@Test
	void upstreamAuthFailureIsBadGatewayNotUnauthorized() throws Exception {
		var id = UUID.randomUUID();
		given(videoService.transform(id))
				.willThrow(new VideoApiException(ErrorCode.SERVICE_AUTH_FAILED, "req-401", null));

		mockMvc.perform(post("/api/videos/{id}/transform", id))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("SERVICE_AUTH_FAILED"));
	}

	@Test
	void transformedServesMp4Bytes() throws Exception {
		var id = UUID.randomUUID();
		var bytes = VideoFixtures.mp4Bytes(1000);
		given(videoService.getTransformed(id)).willReturn(bytes);

		mockMvc.perform(get("/api/videos/{id}/transformed", id))
				.andExpect(status().isOk())
				.andExpect(content().contentType("video/mp4"))
				.andExpect(content().bytes(bytes));
	}

	@Test
	void transformedSupportsRangeRequestsForVideoPlayback() throws Exception {
		var id = UUID.randomUUID();
		given(videoService.getTransformed(id)).willReturn(VideoFixtures.mp4Bytes(1000));

		mockMvc.perform(get("/api/videos/{id}/transformed", id).header("Range", "bytes=0-99"))
				.andExpect(status().isPartialContent())
				.andExpect(header().string("Content-Range", "bytes 0-99/1000"));
	}

	@Test
	void transformedErrorIsJson() throws Exception {
		var id = UUID.randomUUID();
		given(videoService.getTransformed(id)).willThrow(new VideoApiException(ErrorCode.NOT_TRANSFORMED));

		mockMvc.perform(get("/api/videos/{id}/transformed", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_TRANSFORMED"));
	}

	private void assertUploadError(ErrorCode code, int expectedStatus) throws Exception {
		given(videoService.upload(any())).willThrow(new VideoApiException(code));

		mockMvc.perform(multipart("/api/videos").file(VideoFixtures.mp4Part(16)))
				.andExpect(status().is(expectedStatus))
				.andExpect(jsonPath("$.code").value(code.name()))
				.andExpect(jsonPath("$.message").value(code.userMessage()))
				.andExpect(jsonPath("$.requestId").isEmpty())
				.andExpect(jsonPath("$.retryAfterSeconds").isEmpty());
	}
}
