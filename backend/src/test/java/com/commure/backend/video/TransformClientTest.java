package com.commure.backend.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TransformClientTest {

	private static final String URL = "https://upstream.test/api/transform";
	private static final byte[] GRAYSCALE = VideoFixtures.mp4Bytes(512);

	private MockRestServiceServer server;

	private TransformClient client(String baseUrl, String token, long maxOutputBytes, int maxRetries) {
		return client(baseUrl, token, maxOutputBytes, maxRetries, 1000);
	}

	private TransformClient client(String baseUrl, String token, long maxOutputBytes, int maxRetries,
			int maxRequestsPerSecond) {
		var builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		var properties = new TransformProperties(baseUrl, token, maxOutputBytes, 6, maxRequestsPerSecond, maxRetries,
				Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1));
		return new TransformClient(properties, builder);
	}

	@Test
	void pacesRequestStartsToTheRequestsPerSecondLimit() {
		var client = client("https://upstream.test", "secret-token", 4_000_000, 0, 10);
		server.expect(times(5), requestTo(URL)).andRespond(withSuccess(GRAYSCALE, TransformClient.VIDEO_MP4));

		long start = System.nanoTime();
		for (int i = 0; i < 5; i++) {
			client.transform(GRAYSCALE, "a.mp4");
		}
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertThat(elapsedMillis).as("5 requests at 10/s need at least 4 intervals").isGreaterThanOrEqualTo(380);
		server.verify();
	}

	private TransformClient client(int maxRetries) {
		return client("https://upstream.test", "secret-token", 4_000_000, maxRetries);
	}

	@Test
	void sendsBearerTokenAndMultipartClipLikeTheCurlExample() {
		var client = client(0);
		server.expect(once(), requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-token"))
				.andExpect(request -> {
					assertThat(request.getHeaders().getContentType().toString()).startsWith("multipart/form-data");
					String body = ((MockClientHttpRequest) request).getBodyAsString();
					assertThat(body)
							.contains("Content-Disposition: form-data; name=\"clip\"; filename=\"short.mp4\"")
							.contains("Content-Type: video/mp4");
				})
				.andRespond(withSuccess(GRAYSCALE, TransformClient.VIDEO_MP4).header("X-Request-Id", "req-1"));

		var result = client.transform(VideoFixtures.mp4Bytes(128), "short.mp4");

		assertThat(result.video()).isEqualTo(GRAYSCALE);
		assertThat(result.requestId()).isEqualTo("req-1");
		server.verify();
	}

	@Test
	void toleratesTrailingSlashInBaseUrl() {
		var client = client("https://upstream.test/", "secret-token", 4_000_000, 0);
		server.expect(requestTo(URL)).andRespond(withSuccess(GRAYSCALE, TransformClient.VIDEO_MP4));

		assertThat(client.transform(GRAYSCALE, "a.mp4").video()).isEqualTo(GRAYSCALE);
	}

	@ParameterizedTest
	@CsvSource({
			"400, INVALID_REQUEST",
			"401, SERVICE_AUTH_FAILED",
			"403, SESSION_NOT_LIVE",
			"413, LIMIT_EXCEEDED",
			"422, UNSUPPORTED_VIDEO",
			"429, RATE_LIMITED",
			"503, SERVICE_UNAVAILABLE",
			"500, UPSTREAM_ERROR",
			"504, TIMEOUT"
	})
	void mapsUpstreamStatusToErrorCodeAndKeepsRequestId(int status, ErrorCode expected) {
		var client = client(0);
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status))
				.header("X-Request-Id", "req-" + status)
				.body("{\"error\":\"upstream detail\"}"));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class, ex -> {
					assertThat(ex.code()).isEqualTo(expected);
					assertThat(ex.requestId()).isEqualTo("req-" + status);
				});
	}

	@Test
	void propagatesRetryAfterWhenItExceedsMaxRetryWait() {
		var client = client(2);
		server.expect(once(), requestTo(URL))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "30"));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class, ex -> {
					assertThat(ex.code()).isEqualTo(ErrorCode.RATE_LIMITED);
					assertThat(ex.retryAfterSeconds()).isEqualTo(30);
				});
		server.verify();
	}

	@Test
	void retriesTemporaryFailureThenSucceeds() {
		var client = client(2);
		server.expect(once(), requestTo(URL))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "0"));
		server.expect(once(), requestTo(URL))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "0"));
		server.expect(once(), requestTo(URL))
				.andRespond(withSuccess(GRAYSCALE, TransformClient.VIDEO_MP4).header("X-Request-Id", "req-ok"));

		assertThat(client.transform(GRAYSCALE, "a.mp4").requestId()).isEqualTo("req-ok");
		server.verify();
	}

	@Test
	void givesUpAfterMaxRetries() {
		var client = client(1);
		server.expect(times(2), requestTo(URL))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
						.header(HttpHeaders.RETRY_AFTER, "0")
						.header("X-Request-Id", "req-503"));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class, ex -> {
					assertThat(ex.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
					assertThat(ex.retryAfterSeconds()).isZero();
					assertThat(ex.requestId()).isEqualTo("req-503");
				});
		server.verify();
	}

	@Test
	void doesNotRetryPermanentErrors() {
		var client = client(2);
		server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UNSUPPORTED_VIDEO));
		server.verify();
	}

	@Test
	void rejectsOutputOverLimit() {
		var client = client("https://upstream.test", "secret-token", 100, 0);
		server.expect(requestTo(URL)).andRespond(withSuccess(new byte[101], TransformClient.VIDEO_MP4));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.OUTPUT_TOO_LARGE));
	}

	@Test
	void acceptsOutputExactlyAtLimit() {
		var client = client("https://upstream.test", "secret-token", 100, 0);
		server.expect(requestTo(URL)).andRespond(withSuccess(new byte[100], TransformClient.VIDEO_MP4));

		assertThat(client.transform(GRAYSCALE, "a.mp4").video()).hasSize(100);
	}

	@Test
	void emptySuccessBodyIsAnUpstreamError() {
		var client = client(0);
		server.expect(requestTo(URL)).andRespond(withSuccess(new byte[0], TransformClient.VIDEO_MP4));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_ERROR));
	}

	@Test
	void ioFailureIsReportedAsTimeout() {
		var client = client(0);
		server.expect(requestTo(URL)).andRespond(withException(new HttpTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TIMEOUT));
	}

	@Test
	void connectionFailureIsReportedAsTimeout() {
		var client = client(0);
		server.expect(requestTo(URL)).andRespond(withException(new IOException("connection refused")));

		assertThatThrownBy(() -> client.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TIMEOUT));
	}

	@Test
	void missingConfigurationFailsWithoutCallingUpstream() {
		var blankUrl = client("", "secret-token", 4_000_000, 0);
		assertThatThrownBy(() -> blankUrl.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.NOT_CONFIGURED));

		var blankToken = client("https://upstream.test", " ", 4_000_000, 0);
		assertThatThrownBy(() -> blankToken.transform(GRAYSCALE, "a.mp4"))
				.isInstanceOfSatisfying(VideoApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.NOT_CONFIGURED));
		server.verify();
	}

	@Test
	void parsesRetryAfterSecondsAndHttpDate() {
		assertThat(TransformClient.parseRetryAfter("5")).isEqualTo(5);
		assertThat(TransformClient.parseRetryAfter(" 12 ")).isEqualTo(12);
		assertThat(TransformClient.parseRetryAfter("-3")).isZero();
		assertThat(TransformClient.parseRetryAfter(null)).isNull();
		assertThat(TransformClient.parseRetryAfter("soon")).isNull();

		String inTenSeconds = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now().plusSeconds(10));
		assertThat(TransformClient.parseRetryAfter(inTenSeconds)).isBetween(8L, 10L);
	}
}
