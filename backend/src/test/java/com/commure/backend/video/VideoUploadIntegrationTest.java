package com.commure.backend.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:video_upload;DB_CLOSE_DELAY=-1"
})
class VideoUploadIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private VideoStore store;

	private RestClient client;

	@BeforeEach
	void setUp() {
		client = RestClient.create("http://localhost:" + port);
	}

	@Test
	void uploadsMp4OverRealHttpAndStoresIt() {
		var response = upload("clip.mp4", "video/mp4", VideoFixtures.mp4Bytes(1_100_000));

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		var id = UUID.fromString((String) response.getBody().get("id"));
		assertThat(store.find(id)).hasValueSatisfying(v -> assertThat(v.original()).hasSize(1_100_000));
	}

	@Test
	void rejectsTextFileWith415() {
		var response = upload("notes.txt", "text/plain", "hello".getBytes());

		assertThat(response.getStatusCode().value()).isEqualTo(415);
		assertThat(response.getBody()).containsEntry("code", "UNSUPPORTED_TYPE");
	}

	@Test
	void rejectsVideoOverFiftyMillionBytesWith413() {
		var response = upload("big.mp4", "video/mp4", VideoFixtures.mp4Bytes(50_000_001));

		assertThat(response.getStatusCode().value()).isEqualTo(413);
		assertThat(response.getBody())
				.containsEntry("code", "FILE_TOO_LARGE")
				.containsEntry("message", ErrorCode.FILE_TOO_LARGE.userMessage());
	}

	@Test
	void rejectsLargeFileThatFfmpegCannotReadWith422() {
		assumeTrue(FfmpegFixtures.available(), "needs ffmpeg");

		var response = upload("broken.mp4", "video/mp4", VideoFixtures.mp4Bytes(3_500_000));

		assertThat(response.getStatusCode().value()).isEqualTo(422);
		assertThat(response.getBody())
				.containsEntry("code", "UNREADABLE_VIDEO")
				.containsEntry("message", ErrorCode.UNREADABLE_VIDEO.userMessage());
	}

	@Test
	void multipartLimitAlsoReturnsFriendly413() {
		var response = upload("huge.mp4", "video/mp4", VideoFixtures.mp4Bytes(53_000_000));

		assertThat(response.getStatusCode().value()).isEqualTo(413);
		assertThat(response.getBody()).containsEntry("code", "FILE_TOO_LARGE");
	}

	private ResponseEntity<Map<String, Object>> upload(String filename, String contentType, byte[] bytes) {
		var parts = new MultipartBodyBuilder();
		parts.part("file", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		}).contentType(MediaType.parseMediaType(contentType));

		return client.post()
				.uri("/api/videos")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts.build())
				.retrieve()
				.onStatus(status -> true, (request, response) -> {
				})
				.toEntity(new ParameterizedTypeReference<>() {
				});
	}
}
