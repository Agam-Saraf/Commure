package com.commure.backend.video;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoService {

	private static final Logger log = LoggerFactory.getLogger(VideoService.class);
	private static final byte[] FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);
	private static final String DEFAULT_FILENAME = "clip.mp4";

	private final VideoStore store;
	private final TransformClient transformClient;
	private final VideoProcessor processor;
	private final VideoProperties properties;

	public VideoService(VideoStore store, TransformClient transformClient, VideoProcessor processor,
			VideoProperties properties) {
		this.store = store;
		this.transformClient = transformClient;
		this.processor = processor;
		this.properties = properties;
	}

	public StoredVideo upload(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new VideoApiException(ErrorCode.EMPTY_FILE);
		}
		if (!isMp4ContentType(file.getContentType())) {
			throw new VideoApiException(ErrorCode.UNSUPPORTED_TYPE);
		}
		if (file.getSize() > properties.maxUploadBytes()) {
			throw new VideoApiException(ErrorCode.FILE_TOO_LARGE);
		}
		byte[] bytes = readBytes(file);
		if (!hasMp4Signature(bytes)) {
			throw new VideoApiException(ErrorCode.UNSUPPORTED_TYPE);
		}
		VideoInfo info = probe(bytes);
		if (info != null && info.durationSeconds() > properties.maxDurationSeconds()) {
			throw new VideoApiException(ErrorCode.VIDEO_TOO_LONG);
		}
		int segments = needsSplitting(bytes.length, info)
				? VideoProcessor.segmentCount(info.durationSeconds(), properties.segmentSeconds())
				: 1;
		return store.save(StoredVideo.of(filenameOf(file), bytes, info, segments));
	}

	/** Converts once per upload; repeated calls return the stored grayscale result. */
	public StoredVideo transform(UUID id) {
		StoredVideo video = find(id);
		if (video.isTransformed()) {
			return video;
		}
		TransformResult result = needsSplitting(video.original().length, video.info())
				? transformInParts(video)
				: transformClient.transform(video.original(), video.filename());
		return store.update(id, current -> current.withTransformed(result.video(), result.requestId()))
				.orElseThrow(() -> new VideoApiException(ErrorCode.NOT_FOUND));
	}

	public byte[] getTransformed(UUID id) {
		StoredVideo video = find(id);
		if (!video.isTransformed()) {
			throw new VideoApiException(ErrorCode.NOT_TRANSFORMED);
		}
		return video.transformed();
	}

	/**
	 * Clips the API accepts as-is are sent unchanged. Without metadata (no ffprobe, or a small file
	 * it can't read) the API stays the authority, as it was before splitting existed.
	 */
	private boolean needsSplitting(long sizeBytes, VideoInfo info) {
		if (info == null) {
			return false;
		}
		int longSide = Math.max(info.width(), info.height());
		int shortSide = Math.min(info.width(), info.height());
		return sizeBytes > properties.maxClipBytes()
				|| info.durationSeconds() > properties.maxClipSeconds()
				|| info.estimatedFrames() > properties.maxClipFrames()
				|| longSide > properties.maxClipLongSide()
				|| shortSide > properties.maxClipShortSide();
	}

	private TransformResult transformInParts(StoredVideo video) {
		List<byte[]> parts = processor.split(video.original(), video.info().durationSeconds());
		log.info("Transforming {} in {} parts", video.id(), parts.size());
		TransformResult[] results = transformAll(parts, video.filename());
		byte[] joined = processor.concat(Arrays.stream(results).map(TransformResult::video).toList());
		String requestIds = Arrays.stream(results)
				.map(TransformResult::requestId)
				.filter(Objects::nonNull)
				.collect(Collectors.joining(","));
		return new TransformResult(joined, requestIds.isEmpty() ? null : requestIds);
	}

	private record IndexedResult(int index, TransformResult result) {
	}

	/**
	 * Submits every part at once; {@link TransformClient} enforces the API's concurrency and rate
	 * limits. Results are slotted by part index, so the temporal order never depends on which
	 * response arrives first. The first failure cancels the remaining parts.
	 */
	private TransformResult[] transformAll(List<byte[]> parts, String filename) {
		var results = new TransformResult[parts.size()];
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var completion = new ExecutorCompletionService<IndexedResult>(executor);
			for (int i = 0; i < parts.size(); i++) {
				int index = i;
				completion.submit(() -> {
					try {
						return new IndexedResult(index,
								transformClient.transform(parts.get(index), partFilename(filename, index)));
					}
					catch (VideoApiException ex) {
						throw new PartFailure(index, ex);
					}
				});
			}
			for (int done = 0; done < parts.size(); done++) {
				try {
					IndexedResult next = completion.take().get();
					results[next.index()] = next.result();
				}
				catch (ExecutionException ex) {
					executor.shutdownNow();
					throw partFailure(ex.getCause(), parts.size());
				}
				catch (InterruptedException ex) {
					executor.shutdownNow();
					Thread.currentThread().interrupt();
					throw new VideoApiException(ErrorCode.SERVICE_UNAVAILABLE);
				}
			}
		}
		return results;
	}

	private VideoApiException partFailure(Throwable cause, int total) {
		if (cause instanceof PartFailure failure) {
			return failure.error().withContext("Part %d of %d".formatted(failure.index() + 1, total));
		}
		log.error("Unexpected failure transforming a part", cause);
		return new VideoApiException(ErrorCode.UPSTREAM_ERROR);
	}

	private static final class PartFailure extends RuntimeException {

		private final int index;
		private final VideoApiException error;

		PartFailure(int index, VideoApiException error) {
			super(error.getMessage(), error, false, false);
			this.index = index;
			this.error = error;
		}

		int index() {
			return index;
		}

		VideoApiException error() {
			return error;
		}
	}

	private String partFilename(String filename, int index) {
		String base = filename.toLowerCase().endsWith(".mp4") ? filename.substring(0, filename.length() - 4) : filename;
		return "%s.part-%03d.mp4".formatted(base, index + 1);
	}

	private VideoInfo probe(byte[] bytes) {
		boolean apiCanTakeAsIs = bytes.length <= properties.maxClipBytes();
		if (!processor.isAvailable()) {
			if (apiCanTakeAsIs) {
				return null;
			}
			throw new VideoApiException(ErrorCode.PROCESSOR_UNAVAILABLE);
		}
		VideoInfo info = processor.probe(bytes).orElse(null);
		if (info == null && !apiCanTakeAsIs) {
			throw new VideoApiException(ErrorCode.UNREADABLE_VIDEO);
		}
		return info;
	}

	private StoredVideo find(UUID id) {
		return store.find(id).orElseThrow(() -> new VideoApiException(ErrorCode.NOT_FOUND));
	}

	private static boolean isMp4ContentType(String contentType) {
		if (contentType == null) {
			return false;
		}
		try {
			return MediaType.parseMediaType(contentType).equalsTypeAndSubtype(TransformClient.VIDEO_MP4);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	/** ISO base media files carry the {@code ftyp} box type at bytes 4-8. */
	static boolean hasMp4Signature(byte[] bytes) {
		return bytes.length >= 8 && Arrays.equals(bytes, 4, 8, FTYP, 0, 4);
	}

	private static String filenameOf(MultipartFile file) {
		String name = StringUtils.getFilename(file.getOriginalFilename());
		return StringUtils.hasText(name) ? name : DEFAULT_FILENAME;
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read uploaded video", ex);
		}
	}
}
