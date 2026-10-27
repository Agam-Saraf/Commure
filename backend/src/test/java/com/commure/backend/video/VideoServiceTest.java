package com.commure.backend.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class VideoServiceTest {

	private static final VideoInfo SHORT_CLIP = new VideoInfo(8, 854, 480, 24);
	private static final VideoInfo LONG_CLIP = new VideoInfo(25, 854, 480, 24);

	private final VideoStore store = new VideoStore(20, Long.MAX_VALUE);
	private final TransformClient transformClient = mock(TransformClient.class);
	private final VideoProcessor processor = mock(VideoProcessor.class);
	private final VideoService service = new VideoService(store, transformClient, processor,
			VideoFixtures.properties(5_000_000));

	private StoredVideo uploadWithInfo(int size, VideoInfo info) {
		given(processor.isAvailable()).willReturn(true);
		given(processor.probe(any())).willReturn(Optional.ofNullable(info));
		return service.upload(VideoFixtures.mp4Part(size));
	}

	@Nested
	class Upload {

		@Test
		void storesValidMp4() {
			var stored = service.upload(VideoFixtures.mp4Part(1024));

			assertThat(stored.filename()).isEqualTo("clip.mp4");
			assertThat(stored.original()).hasSize(1024);
			assertThat(stored.isTransformed()).isFalse();
			assertThat(store.find(stored.id())).isPresent();
		}

		@Test
		void acceptsExactlyTheUploadLimitAndRejectsOneByteMore() {
			assertThat(uploadWithInfo(5_000_000, LONG_CLIP).original()).hasSize(5_000_000);
			assertCode(VideoFixtures.mp4Part(5_000_001), ErrorCode.FILE_TOO_LARGE);
		}

		@Test
		void rejectsMissingFile() {
			assertThatThrownBy(() -> service.upload(null))
					.isInstanceOfSatisfying(VideoApiException.class,
							ex -> assertThat(ex.code()).isEqualTo(ErrorCode.EMPTY_FILE));
		}

		@Test
		void rejectsEmptyFile() {
			assertCode(new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[0]), ErrorCode.EMPTY_FILE);
		}

		@Test
		void rejectsNonMp4ContentType() {
			assertCode(new MockMultipartFile("file", "notes.txt", "text/plain", VideoFixtures.mp4Bytes(64)),
					ErrorCode.UNSUPPORTED_TYPE);
			assertCode(new MockMultipartFile("file", "clip.mov", "video/quicktime", VideoFixtures.mp4Bytes(64)),
					ErrorCode.UNSUPPORTED_TYPE);
			assertCode(new MockMultipartFile("file", "clip.mp4", null, VideoFixtures.mp4Bytes(64)),
					ErrorCode.UNSUPPORTED_TYPE);
		}

		@Test
		void rejectsMp4ContentTypeWithoutFtypSignature() {
			assertCode(new MockMultipartFile("file", "fake.mp4", "video/mp4", "not really a video".getBytes()),
					ErrorCode.UNSUPPORTED_TYPE);
		}

		@Test
		void stripsPathFromFilenameAndDefaultsWhenBlank() {
			var withPath = new MockMultipartFile("file", "../../etc/clip.mp4", "video/mp4", VideoFixtures.mp4Bytes(64));
			var blank = new MockMultipartFile("file", "", "video/mp4", VideoFixtures.mp4Bytes(64));

			assertThat(service.upload(withPath).filename()).isEqualTo("clip.mp4");
			assertThat(service.upload(blank).filename()).isEqualTo("clip.mp4");
		}

		@Test
		void rejectsVideosLongerThanTwoMinutes() {
			given(processor.isAvailable()).willReturn(true);
			given(processor.probe(any())).willReturn(Optional.of(new VideoInfo(120.5, 640, 360, 30)));

			assertCode(VideoFixtures.mp4Part(1024), ErrorCode.VIDEO_TOO_LONG);
		}

		@Test
		void plansOnePartForClipsTheApiAcceptsAndSeveralForLongOnes() {
			assertThat(uploadWithInfo(1024, SHORT_CLIP).segmentCount()).isEqualTo(1);
			assertThat(uploadWithInfo(1024, LONG_CLIP).segmentCount()).isEqualTo(3);
			assertThat(uploadWithInfo(1024, new VideoInfo(120, 640, 360, 30)).segmentCount()).isEqualTo(13);
		}

		@Test
		void withoutFfmpegSmallClipsAreStillAcceptedAsIs() {
			given(processor.isAvailable()).willReturn(false);

			var stored = service.upload(VideoFixtures.mp4Part(1024));

			assertThat(stored.info()).isNull();
			assertThat(stored.segmentCount()).isEqualTo(1);
		}

		@Test
		void withoutFfmpegLargeVideosAreRejected() {
			given(processor.isAvailable()).willReturn(false);

			assertCode(VideoFixtures.mp4Part(3_000_001), ErrorCode.PROCESSOR_UNAVAILABLE);
		}

		@Test
		void largeUnreadableVideoIsRejectedButSmallOneIsLeftToTheApi() {
			given(processor.isAvailable()).willReturn(true);
			given(processor.probe(any())).willReturn(Optional.empty());

			assertCode(VideoFixtures.mp4Part(3_000_001), ErrorCode.UNREADABLE_VIDEO);
			assertThat(service.upload(VideoFixtures.mp4Part(1024)).info()).isNull();
		}

		private void assertCode(MockMultipartFile file, ErrorCode expected) {
			assertThatThrownBy(() -> service.upload(file))
					.isInstanceOfSatisfying(VideoApiException.class, ex -> assertThat(ex.code()).isEqualTo(expected));
		}
	}

	@Nested
	class Transform {

		@Test
		void storesGrayscaleResultNextToOriginal() {
			var uploaded = service.upload(VideoFixtures.mp4Part(256));
			var grayscale = VideoFixtures.mp4Bytes(128);
			given(transformClient.transform(uploaded.original(), "clip.mp4"))
					.willReturn(new TransformResult(grayscale, "req-1"));

			var transformed = service.transform(uploaded.id());

			assertThat(transformed.transformed()).isEqualTo(grayscale);
			assertThat(transformed.original()).isEqualTo(uploaded.original());
			assertThat(transformed.requestId()).isEqualTo("req-1");
			assertThat(service.getTransformed(uploaded.id())).isEqualTo(grayscale);
		}

		@Test
		void clipWithinApiLimitsIsSentUnchangedWithoutSplitting() {
			var uploaded = uploadWithInfo(1024, SHORT_CLIP);
			given(transformClient.transform(any(), anyString())).willReturn(new TransformResult(new byte[] { 1 }, "r"));

			service.transform(uploaded.id());

			verify(transformClient).transform(uploaded.original(), "clip.mp4");
			verify(processor, never()).split(any(), anyDouble());
			verify(processor, never()).concat(anyList());
		}

		@Test
		void secondTransformReusesStoredResult() {
			var uploaded = service.upload(VideoFixtures.mp4Part(256));
			given(transformClient.transform(any(), anyString()))
					.willReturn(new TransformResult(VideoFixtures.mp4Bytes(64), "req-1"));

			service.transform(uploaded.id());
			service.transform(uploaded.id());

			verify(transformClient, times(1)).transform(any(), anyString());
		}

		@Test
		void unknownIdIsNotFoundWithoutCallingUpstream() {
			assertThatThrownBy(() -> service.transform(UUID.randomUUID()))
					.isInstanceOfSatisfying(VideoApiException.class,
							ex -> assertThat(ex.code()).isEqualTo(ErrorCode.NOT_FOUND));
			verifyNoInteractions(transformClient);
		}

		@Test
		void upstreamFailurePropagatesAndLeavesVideoUntransformed() {
			var uploaded = service.upload(VideoFixtures.mp4Part(256));
			given(transformClient.transform(any(), anyString()))
					.willThrow(new VideoApiException(ErrorCode.RATE_LIMITED, "req-429", 3L));

			assertThatThrownBy(() -> service.transform(uploaded.id()))
					.isInstanceOfSatisfying(VideoApiException.class, ex -> {
						assertThat(ex.code()).isEqualTo(ErrorCode.RATE_LIMITED);
						assertThat(ex.retryAfterSeconds()).isEqualTo(3);
					});
			assertThat(store.find(uploaded.id())).hasValueSatisfying(v -> assertThat(v.isTransformed()).isFalse());
		}

		@Test
		void getTransformedBeforeTransformIsNotTransformed() {
			var uploaded = service.upload(VideoFixtures.mp4Part(256));

			assertThatThrownBy(() -> service.getTransformed(uploaded.id()))
					.isInstanceOfSatisfying(VideoApiException.class,
							ex -> assertThat(ex.code()).isEqualTo(ErrorCode.NOT_TRANSFORMED));
		}

		@Test
		void getTransformedForUnknownIdIsNotFound() {
			assertThatThrownBy(() -> service.getTransformed(UUID.randomUUID()))
					.isInstanceOfSatisfying(VideoApiException.class,
							ex -> assertThat(ex.code()).isEqualTo(ErrorCode.NOT_FOUND));
		}
	}

	@Nested
	class LongVideos {

		private final byte[] part1 = { 1 };
		private final byte[] part2 = { 2 };
		private final byte[] part3 = { 3 };

		private StoredVideo uploadLong() {
			var uploaded = uploadWithInfo(4096, LONG_CLIP);
			given(processor.split(uploaded.original(), 25.0)).willReturn(List.of(part1, part2, part3));
			return uploaded;
		}

		/** Later parts answer first, the way out-of-order API responses would arrive. */
		private void respondInReverseOrder(AtomicInteger peakConcurrency, List<String> completionOrder) {
			var inFlight = new AtomicInteger();
			given(transformClient.transform(any(), anyString())).willAnswer(call -> {
				byte[] part = call.getArgument(0);
				String filename = call.getArgument(1);
				peakConcurrency.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
				try {
					Thread.sleep((4 - part[0]) * 150L);
				}
				finally {
					inFlight.decrementAndGet();
				}
				completionOrder.add(filename);
				return new TransformResult(new byte[] { (byte) (part[0] * 10) }, "req-" + part[0]);
			});
		}

		@Test
		void transformsPartsInParallelAndJoinsThemInTemporalOrder() {
			var uploaded = uploadLong();
			var peak = new AtomicInteger();
			var completionOrder = Collections.synchronizedList(new ArrayList<String>());
			respondInReverseOrder(peak, completionOrder);
			given(processor.concat(anyList())).willReturn(new byte[] { 99 });

			var transformed = service.transform(uploaded.id());

			assertThat(completionOrder).containsExactly("clip.part-003.mp4", "clip.part-002.mp4", "clip.part-001.mp4");
			assertThat(peak.get()).as("parts are sent concurrently").isEqualTo(3);
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<byte[]>> joined = ArgumentCaptor.forClass(List.class);
			verify(processor).concat(joined.capture());
			assertThat(joined.getValue()).containsExactly(new byte[] { 10 }, new byte[] { 20 }, new byte[] { 30 });
			assertThat(transformed.transformed()).containsExactly(99);
			assertThat(transformed.requestId()).isEqualTo("req-1,req-2,req-3");
			assertThat(transformed.segmentCount()).isEqualTo(3);
		}

		@Test
		void shortClipsBreakingOtherApiLimitsAreReencodedThroughTheProcessor() {
			var oversized = uploadWithInfo(3_500_000, SHORT_CLIP);
			var fourK = uploadWithInfo(1024, new VideoInfo(8, 3840, 2160, 30));
			var highFrameRate = uploadWithInfo(1024, new VideoInfo(8, 640, 360, 120));
			given(processor.split(any(), anyDouble())).willReturn(List.of(part1));
			given(processor.concat(anyList())).willReturn(part1);
			given(transformClient.transform(any(), anyString())).willReturn(new TransformResult(part1, "r"));

			for (var video : List.of(oversized, fourK, highFrameRate)) {
				assertThat(video.segmentCount()).isEqualTo(1);
				service.transform(video.id());
			}

			verify(processor, times(3)).split(any(), eq(8.0));
			verify(transformClient, times(3)).transform(eq(part1), anyString());
		}

		@Test
		void failedPartFailsTheWholeVideoWithPartContextAndNothingIsStored() {
			var uploaded = uploadLong();
			given(transformClient.transform(eq(part1), anyString())).willReturn(new TransformResult(part1, "r1"));
			given(transformClient.transform(eq(part2), anyString()))
					.willThrow(new VideoApiException(ErrorCode.UNSUPPORTED_VIDEO, "req-422", null));
			given(transformClient.transform(eq(part3), anyString())).willReturn(new TransformResult(part3, "r3"));

			assertThatThrownBy(() -> service.transform(uploaded.id()))
					.isInstanceOfSatisfying(VideoApiException.class, ex -> {
						assertThat(ex.code()).isEqualTo(ErrorCode.UNSUPPORTED_VIDEO);
						assertThat(ex.getMessage()).isEqualTo("Part 2 of 3: " + ErrorCode.UNSUPPORTED_VIDEO.userMessage());
						assertThat(ex.requestId()).isEqualTo("req-422");
					});
			verify(processor, never()).concat(anyList());
			assertThat(store.find(uploaded.id())).hasValueSatisfying(v -> assertThat(v.isTransformed()).isFalse());
		}

		@Test
		void retryAfterFromAFailedPartIsKept() {
			var uploaded = uploadLong();
			given(transformClient.transform(any(), anyString()))
					.willThrow(new VideoApiException(ErrorCode.RATE_LIMITED, null, 9L));

			assertThatThrownBy(() -> service.transform(uploaded.id()))
					.isInstanceOfSatisfying(VideoApiException.class,
							ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(9));
		}

		@Test
		void splittingFailureSurfacesAsProcessingFailed() {
			var uploaded = uploadWithInfo(4096, LONG_CLIP);
			given(processor.split(any(), anyDouble())).willThrow(new VideoApiException(ErrorCode.PROCESSING_FAILED));

			assertThatThrownBy(() -> service.transform(uploaded.id()))
					.isInstanceOfSatisfying(VideoApiException.class,
							ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PROCESSING_FAILED));
			verifyNoInteractions(transformClient);
		}
	}
}
