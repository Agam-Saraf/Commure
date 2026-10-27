package com.commure.backend.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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

import com.commure.backend.video.FakeTransformServer.Received;
import com.commure.backend.video.FakeTransformServer.Reply;
import com.commure.backend.video.FfmpegFixtures.Section;

/**
 * End-to-end long-video flow with real ffmpeg: the backend splits the upload, sends the parts in
 * parallel to a fake API that answers out of order, and reassembles them in temporal order.
 */
@EnabledIf("com.commure.backend.video.FfmpegFixtures#available")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:long_video;DB_CLOSE_DELAY=-1",
		"transform.token=test-token",
		"transform.max-retries=0"
})
class LongVideoIntegrationTest {

	private static final int RED = 0;
	private static final int GREEN = 1;
	private static final int BLUE = 2;
	private static final Pattern PART_INDEX = Pattern.compile("\\.part-(\\d{3})\\.mp4$");
	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};

	private static final FakeTransformServer upstream = startUpstream();
	private static byte[] rgb25s;

	@LocalServerPort
	private int port;

	private RestClient client;

	@DynamicPropertySource
	static void upstreamProperties(DynamicPropertyRegistry registry) {
		registry.add("transform.base-url", upstream::baseUrl);
	}

	@BeforeAll
	static void fixtures() {
		rgb25s = FfmpegFixtures.colorSections("320x240", "30000/1001",
				new Section("red", 10), new Section("green", 10), new Section("blue", 5));
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
	void splitsTransformsInParallelAndReassemblesInTemporalOrder() {
		upstream.reset(echoLaterPartsFirst(3));

		var uploaded = upload("rgb.mp4", rgb25s);
		assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
		assertThat(uploaded.getBody()).containsEntry("segmentCount", 3);
		assertThat((Double) uploaded.getBody().get("durationSeconds")).isCloseTo(25, within(0.1));
		String id = (String) uploaded.getBody().get("id");

		var transform = transform(id);

		assertThat(transform.getStatusCode().value()).isEqualTo(200);
		assertThat(transform.getBody())
				.containsEntry("transformed", true)
				.containsEntry("requestId", "req-001,req-002,req-003");
		assertThat(upstream.peakInFlight()).as("all three parts in flight together").isEqualTo(3);
		assertThat(upstream.received()).hasSize(3).allSatisfy(request -> {
			assertThat(request.authorization()).isEqualTo("Bearer test-token");
			assertThat(request.body()).contains("Content-Type: video/mp4");
			byte[] clip = request.clip();
			var probe = FfmpegFixtures.probe(clip);
			assertThat(clip.length).isLessThanOrEqualTo(3_000_000);
			assertThat(probe.durationSeconds()).isLessThanOrEqualTo(10.0);
			assertThat(probe.frames()).isLessThanOrEqualTo(600);
			assertThat(probe.codec()).isEqualTo("h264");
		});
		assertThat(upstream.received()).extracting(Received::filename)
				.containsExactlyInAnyOrder("rgb.part-001.mp4", "rgb.part-002.mp4", "rgb.part-003.mp4");

		byte[] video = client.get().uri("/api/videos/{id}/transformed", id).retrieve().body(byte[].class);
		assertThat(FfmpegFixtures.probe(video).durationSeconds()).isCloseTo(25, within(0.2));
		assertThat(FfmpegFixtures.dominantChannelAt(video, 5)).isEqualTo(RED);
		assertThat(FfmpegFixtures.dominantChannelAt(video, 15)).isEqualTo(GREEN);
		assertThat(FfmpegFixtures.dominantChannelAt(video, 22)).isEqualTo(BLUE);
	}

	@Test
	void twoMinuteVideoStaysWithinTheSixRequestConcurrencyLimit() {
		upstream.reset(echoLaterPartsFirst(13));
		byte[] twoMinutes = FfmpegFixtures.colorSections("160x120", "10", new Section("red", 60),
				new Section("blue", 60));

		var uploaded = upload("long.mp4", twoMinutes);
		assertThat(uploaded.getBody()).containsEntry("segmentCount", 13);
		String id = (String) uploaded.getBody().get("id");

		assertThat(transform(id).getStatusCode().value()).isEqualTo(200);

		assertThat(upstream.received()).hasSize(13);
		assertThat(upstream.peakInFlight()).isEqualTo(6);
		byte[] video = client.get().uri("/api/videos/{id}/transformed", id).retrieve().body(byte[].class);
		assertThat(FfmpegFixtures.probe(video).durationSeconds()).isCloseTo(120, within(0.5));
		assertThat(FfmpegFixtures.dominantChannelAt(video, 30)).isEqualTo(RED);
		assertThat(FfmpegFixtures.dominantChannelAt(video, 90)).isEqualTo(BLUE);
	}

	@Test
	void oneFailedPartFailsTheVideoWithAFriendlyMessageAndNothingIsStored() {
		upstream.reset((arrival, request) -> partIndex(request) == 2
				? Reply.error(422, "req-bad", null)
				: Reply.video(request.clip(), "req-ok"));
		String id = (String) upload("rgb.mp4", rgb25s).getBody().get("id");

		var transform = transform(id);

		assertThat(transform.getStatusCode().value()).isEqualTo(422);
		assertThat(transform.getBody())
				.containsEntry("code", "UNSUPPORTED_VIDEO")
				.containsEntry("message", "Part 2 of 3: " + ErrorCode.UNSUPPORTED_VIDEO.userMessage())
				.containsEntry("requestId", "req-bad");
		var download = client.get()
				.uri("/api/videos/{id}/transformed", id)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(JSON);
		assertThat(download.getStatusCode().value()).isEqualTo(404);
		assertThat(download.getBody()).containsEntry("code", "NOT_TRANSFORMED");
	}

	@Test
	void rejectsVideosLongerThanTwoMinutesBeforeCallingTheApi() {
		upstream.reset(Reply.video(new byte[] { 1 }, "unused"));
		byte[] tooLong = FfmpegFixtures.colorSections("160x120", "5", new Section("gray", 121));

		var uploaded = upload("too-long.mp4", tooLong);

		assertThat(uploaded.getStatusCode().value()).isEqualTo(422);
		assertThat(uploaded.getBody())
				.containsEntry("code", "VIDEO_TOO_LONG")
				.containsEntry("message", ErrorCode.VIDEO_TOO_LONG.userMessage());
		assertThat(upstream.received()).isEmpty();
	}

	/** Echoes each part back, answering later parts sooner so completions arrive out of order. */
	private static FakeTransformServer.Responder echoLaterPartsFirst(int parts) {
		return (arrival, request) -> {
			int index = partIndex(request);
			return Reply.video(request.clip(), "req-%03d".formatted(index))
				.delayed(Duration.ofMillis(200L + 200L * (parts + 1 - index)));
		};
	}

	private static int partIndex(Received request) {
		var matcher = PART_INDEX.matcher(request.filename());
		assertThat(matcher.find()).as("part filename %s", request.filename()).isTrue();
		return Integer.parseInt(matcher.group(1));
	}

	private ResponseEntity<Map<String, Object>> upload(String filename, byte[] bytes) {
		var parts = new MultipartBodyBuilder();
		parts.part("file", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		}).contentType(MediaType.valueOf("video/mp4"));
		return client.post()
				.uri("/api/videos")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts.build())
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> transform(String id) {
		return client.post()
				.uri("/api/videos/{id}/transform", id)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(JSON);
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
