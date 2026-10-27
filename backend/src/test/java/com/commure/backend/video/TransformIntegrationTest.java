package com.commure.backend.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.commure.backend.video.FakeTransformServer.Reply;

/**
 * Full HTTP simulation: real Tomcat backend calling a fake third-party API on a real socket, so
 * multipart encoding, headers, status mapping, retries, timeouts and the concurrency cap are all
 * exercised end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:video_transform;DB_CLOSE_DELAY=-1",
		"transform.token=test-token",
		"transform.max-retries=1",
		"transform.max-retry-wait=1s",
		"transform.read-timeout=1s"
})
class TransformIntegrationTest {

	private static final FakeTransformServer upstream = startUpstream();
	private static final byte[] GRAYSCALE = VideoFixtures.mp4Bytes(4096);
	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};

	@LocalServerPort
	private int port;

	private RestClient client;

	@DynamicPropertySource
	static void upstreamProperties(DynamicPropertyRegistry registry) {
		registry.add("transform.base-url", upstream::baseUrl);
	}

	@AfterAll
	static void stopUpstream() {
		upstream.close();
	}

	@BeforeEach
	void setUp() {
		client = RestClient.create("http://localhost:" + port);
	}

	@Test
	void uploadTransformAndDownloadGrayscale() {
		upstream.reset(Reply.video(GRAYSCALE, "req-123"));
		String id = upload();

		var transform = transform(id);

		assertThat(transform.getStatusCode().value()).isEqualTo(200);
		assertThat(transform.getBody())
				.containsEntry("transformed", true)
				.containsEntry("requestId", "req-123")
				.containsEntry("transformedSizeBytes", GRAYSCALE.length);

		var download = client.get().uri("/api/videos/{id}/transformed", id).retrieve().toEntity(byte[].class);
		assertThat(download.getHeaders().getContentType()).hasToString("video/mp4");
		assertThat(download.getBody()).isEqualTo(GRAYSCALE);

		assertThat(upstream.received()).singleElement().satisfies(request -> {
			assertThat(request.authorization()).isEqualTo("Bearer test-token");
			assertThat(request.contentType()).startsWith("multipart/form-data");
			assertThat(request.body())
					.contains("name=\"clip\"; filename=\"clip.mp4\"")
					.contains("Content-Type: video/mp4");
		});
	}

	@ParameterizedTest
	@CsvSource({
			"400, 400, INVALID_REQUEST",
			"401, 502, SERVICE_AUTH_FAILED",
			"403, 503, SESSION_NOT_LIVE",
			"413, 413, LIMIT_EXCEEDED",
			"422, 422, UNSUPPORTED_VIDEO",
			"500, 502, UPSTREAM_ERROR"
	})
	void upstreamErrorsBecomeFriendlyJson(int upstreamStatus, int expectedStatus, ErrorCode expectedCode) {
		upstream.reset(Reply.error(upstreamStatus, "req-" + upstreamStatus, null));
		String id = upload();

		var response = transform(id);

		assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
		assertThat(response.getBody())
				.containsEntry("code", expectedCode.name())
				.containsEntry("message", expectedCode.userMessage())
				.containsEntry("requestId", "req-" + upstreamStatus);
		assertThat(upstream.received()).hasSize(1);
		assertNotTransformed(id);
	}

	@Test
	void rateLimitWithLongRetryAfterIsPassedThroughWithoutRetrying() {
		upstream.reset(Reply.error(429, "req-429", "7"));
		String id = upload();

		var response = transform(id);

		assertThat(response.getStatusCode().value()).isEqualTo(429);
		assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("7");
		assertThat(response.getBody())
				.containsEntry("code", "RATE_LIMITED")
				.containsEntry("retryAfterSeconds", 7)
				.containsEntry("requestId", "req-429");
		assertThat(upstream.received()).hasSize(1);
	}

	@Test
	void temporaryUnavailabilityIsRetriedTransparently() {
		upstream.reset(Reply.error(503, "req-503", "0"), Reply.video(GRAYSCALE, "req-ok"));
		String id = upload();

		var response = transform(id);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).containsEntry("requestId", "req-ok");
		assertThat(upstream.received()).hasSize(2);
	}

	@Test
	void persistentUnavailabilityGivesUpAfterMaxRetries() {
		upstream.reset(Reply.error(503, "req-503", "0"));
		String id = upload();

		var response = transform(id);

		assertThat(response.getStatusCode().value()).isEqualTo(503);
		assertThat(response.getBody()).containsEntry("code", "SERVICE_UNAVAILABLE");
		assertThat(upstream.received()).hasSize(2);
	}

	@Test
	void slowUpstreamTimesOutAs504() {
		upstream.reset(Reply.video(GRAYSCALE, "req-slow").delayed(Duration.ofMillis(1500)));
		String id = upload();

		var response = transform(id);

		assertThat(response.getStatusCode().value()).isEqualTo(504);
		assertThat(response.getBody()).containsEntry("code", "TIMEOUT");
	}

	@Test
	void outputOverFourMegabytesIsRejected() {
		upstream.reset(Reply.video(new byte[4_000_001], "req-big"));
		String id = upload();

		var response = transform(id);

		assertThat(response.getStatusCode().value()).isEqualTo(502);
		assertThat(response.getBody()).containsEntry("code", "OUTPUT_TOO_LARGE");
		assertNotTransformed(id);
	}

	@Test
	void unknownVideoIsNotFound() {
		upstream.reset(Reply.video(GRAYSCALE, "unused"));

		var response = transform(UUID.randomUUID().toString());

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).containsEntry("code", "NOT_FOUND");
		assertThat(upstream.received()).isEmpty();
	}

	@Test
	void neverExceedsSixConcurrentUpstreamRequests() throws Exception {
		// Long enough that six paced starts (1/12 s apart) overlap, yet under the 1s read timeout.
		upstream.reset(Reply.video(GRAYSCALE, "req-par").delayed(Duration.ofMillis(800)));
		var ids = new ArrayList<String>();
		for (int i = 0; i < 9; i++) {
			ids.add(upload());
		}

		try (var pool = Executors.newFixedThreadPool(ids.size())) {
			var futures = new ArrayList<Future<ResponseEntity<Map<String, Object>>>>();
			for (String id : ids) {
				futures.add(pool.submit(() -> transform(id)));
			}
			for (var future : futures) {
				assertThat(future.get().getStatusCode().value()).isEqualTo(200);
			}
		}

		assertThat(upstream.received()).hasSize(9);
		assertThat(upstream.peakInFlight()).isEqualTo(6);
	}

	private String upload() {
		var parts = new MultipartBodyBuilder();
		parts.part("file", new ByteArrayResource(VideoFixtures.mp4Bytes(2048)) {
			@Override
			public String getFilename() {
				return "clip.mp4";
			}
		}).contentType(MediaType.valueOf("video/mp4"));
		var body = client.post()
				.uri("/api/videos")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts.build())
				.retrieve()
				.body(JSON);
		return (String) body.get("id");
	}

	private ResponseEntity<Map<String, Object>> transform(String id) {
		return client.post()
				.uri("/api/videos/{id}/transform", id)
				.retrieve()
				.onStatus(status -> true, (request, response) -> {
				})
				.toEntity(JSON);
	}

	private void assertNotTransformed(String id) {
		var response = client.get()
				.uri("/api/videos/{id}/transformed", id)
				.retrieve()
				.onStatus(status -> true, (request, response1) -> {
				})
				.toEntity(JSON);
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).containsEntry("code", "NOT_TRANSFORMED");
	}

	private static FakeTransformServer startUpstream() {
		try {
			return new FakeTransformServer();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not start fake transform server", ex);
		}
	}
}
