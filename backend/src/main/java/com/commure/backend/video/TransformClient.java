package com.commure.backend.video;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Sends a clip to the third-party grayscale API, equivalent to
 * {@code curl -H "Authorization: Bearer $TOKEN" -F "clip=@x.mp4;type=video/mp4" $BASE_URL/api/transform}.
 */
public class TransformClient {

	static final String REQUEST_ID_HEADER = "X-Request-Id";
	static final MediaType VIDEO_MP4 = MediaType.valueOf("video/mp4");

	private static final Logger log = LoggerFactory.getLogger(TransformClient.class);
	private static final long DEFAULT_RETRY_WAIT_SECONDS = 1;

	private final TransformProperties properties;
	private final RestClient restClient;
	private final Semaphore permits;
	private final long requestIntervalNanos;
	private final Object rateLock = new Object();
	private long nextRequestAtNanos = System.nanoTime();

	public TransformClient(TransformProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.build();
		this.permits = new Semaphore(properties.maxConcurrent(), true);
		this.requestIntervalNanos = TimeUnit.SECONDS.toNanos(1) / properties.maxRequestsPerSecond();
	}

	public TransformResult transform(byte[] video, String filename) {
		if (!properties.isConfigured()) {
			throw new VideoApiException(ErrorCode.NOT_CONFIGURED);
		}
		acquirePermit();
		try {
			return sendWithRetry(video, filename);
		}
		finally {
			permits.release();
		}
	}

	private TransformResult sendWithRetry(byte[] video, String filename) {
		for (int attempt = 0;; attempt++) {
			try {
				return send(video, filename);
			}
			catch (VideoApiException ex) {
				long waitSeconds = ex.retryAfterSeconds() != null ? ex.retryAfterSeconds() : DEFAULT_RETRY_WAIT_SECONDS;
				if (!isRetryable(ex.code()) || attempt >= properties.maxRetries()
						|| waitSeconds > properties.maxRetryWait().toSeconds()) {
					throw ex;
				}
				log.info("Transform attempt {} got {} (request {}); retrying in {}s",
						attempt + 1, ex.code(), ex.requestId(), waitSeconds);
				sleep(Duration.ofSeconds(waitSeconds));
			}
		}
	}

	private TransformResult send(byte[] video, String filename) {
		var parts = new MultipartBodyBuilder();
		parts.part("clip", new ByteArrayResource(video) {
			@Override
			public String getFilename() {
				return filename;
			}
		}).contentType(VIDEO_MP4);

		awaitRateSlot();
		try {
			return restClient.post()
					.uri(StringUtils.trimTrailingCharacter(properties.baseUrl(), '/') + "/api/transform")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(parts.build())
					.exchange((request, response) -> handle(response));
		}
		catch (ResourceAccessException ex) {
			log.warn("Transform service unreachable: {}", ex.getMessage());
			throw new VideoApiException(ErrorCode.TIMEOUT);
		}
	}

	private TransformResult handle(ClientHttpResponse response) throws IOException {
		HttpHeaders headers = response.getHeaders();
		String requestId = headers.getFirst(REQUEST_ID_HEADER);
		int status = response.getStatusCode().value();
		if (response.getStatusCode().is2xxSuccessful()) {
			byte[] body = readLimited(response.getBody(), headers.getContentLength(), requestId);
			if (body.length == 0) {
				throw new VideoApiException(ErrorCode.UPSTREAM_ERROR, requestId, null);
			}
			return new TransformResult(body, requestId);
		}
		ErrorCode code = codeFor(status);
		log.warn("Transform service returned {} -> {} (request {})", status, code, requestId);
		throw new VideoApiException(code, requestId, parseRetryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)));
	}

	private byte[] readLimited(InputStream body, long contentLength, String requestId) throws IOException {
		long max = properties.maxOutputBytes();
		if (contentLength > max) {
			throw new VideoApiException(ErrorCode.OUTPUT_TOO_LARGE, requestId, null);
		}
		byte[] bytes = body.readNBytes(Math.toIntExact(max + 1));
		if (bytes.length > max) {
			throw new VideoApiException(ErrorCode.OUTPUT_TOO_LARGE, requestId, null);
		}
		return bytes;
	}

	static ErrorCode codeFor(int status) {
		return switch (status) {
			case 400 -> ErrorCode.INVALID_REQUEST;
			case 401 -> ErrorCode.SERVICE_AUTH_FAILED;
			case 403 -> ErrorCode.SESSION_NOT_LIVE;
			case 413 -> ErrorCode.LIMIT_EXCEEDED;
			case 422 -> ErrorCode.UNSUPPORTED_VIDEO;
			case 429 -> ErrorCode.RATE_LIMITED;
			case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
			case 504 -> ErrorCode.TIMEOUT;
			default -> ErrorCode.UPSTREAM_ERROR;
		};
	}

	/** Accepts delta-seconds or an HTTP-date; returns null when absent or unparseable. */
	static Long parseRetryAfter(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return Math.max(0, Long.parseLong(value.strip()));
		}
		catch (NumberFormatException ignored) {
			// Fall through to the HTTP-date form.
		}
		try {
			var at = ZonedDateTime.parse(value.strip(), DateTimeFormatter.RFC_1123_DATE_TIME);
			return Math.max(0, Duration.between(ZonedDateTime.now(at.getZone()), at).toSeconds());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static boolean isRetryable(ErrorCode code) {
		return code == ErrorCode.RATE_LIMITED || code == ErrorCode.SERVICE_UNAVAILABLE;
	}

	private void acquirePermit() {
		try {
			if (!permits.tryAcquire(properties.queueTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
				throw new VideoApiException(ErrorCode.RATE_LIMITED, null, DEFAULT_RETRY_WAIT_SECONDS);
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new VideoApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
	}

	/** Spaces request starts evenly so bursts of parts stay under the per-second limit. */
	private void awaitRateSlot() {
		long waitNanos;
		synchronized (rateLock) {
			long now = System.nanoTime();
			long slot = Math.max(now, nextRequestAtNanos);
			nextRequestAtNanos = slot + requestIntervalNanos;
			waitNanos = slot - now;
		}
		if (waitNanos > 0) {
			sleep(Duration.ofNanos(waitNanos));
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new VideoApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
	}
}
