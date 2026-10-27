package com.commure.backend.video;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

/**
 * ffmpeg/ffprobe wrapper for long videos: probes metadata, splits into ordered H.264 parts that
 * fit the transform API's per-clip limits, and joins the grayscale parts back in order.
 */
@Component
public class VideoProcessor {

	private static final Logger log = LoggerFactory.getLogger(VideoProcessor.class);
	private static final int MAX_FPS = 60;
	/** Forced keyframes can land a fraction of a frame before the requested cut; without slack ffmpeg skips the cut. */
	private static final String SEGMENT_TIME_DELTA = "0.05";

	private final VideoProperties properties;
	private volatile Boolean available;

	public VideoProcessor(VideoProperties properties) {
		this.properties = properties;
	}

	public boolean isAvailable() {
		if (available == null) {
			available = canRun(properties.ffmpeg().ffprobePath()) && canRun(properties.ffmpeg().ffmpegPath());
			if (!available) {
				log.warn("ffmpeg/ffprobe not found; long videos cannot be processed");
			}
		}
		return available;
	}

	/** Empty when the bytes are not a readable video. */
	public Optional<VideoInfo> probe(byte[] video) {
		return inWorkDir(dir -> {
			Path input = write(dir, "input.mp4", video);
			Path output = dir.resolve("probe.txt");
			boolean ok = run(List.of(properties.ffmpeg().ffprobePath(), "-v", "error",
					"-select_streams", "v:0",
					"-show_entries", "stream=width,height,avg_frame_rate:format=duration",
					"-of", "default=noprint_wrappers=1", input.toString()), dir, output);
			return ok ? parseProbe(Files.readString(output)) : Optional.empty();
		});
	}

	/**
	 * Re-encodes into {@code ceil(duration / segmentSeconds)} equal parts, cut at forced keyframes so
	 * every part starts cleanly. Parts are returned in temporal order.
	 */
	public List<byte[]> split(byte[] video, double durationSeconds) {
		int parts = segmentCount(durationSeconds, properties.segmentSeconds());
		return inWorkDir(dir -> {
			Path input = write(dir, "input.mp4", video);
			var command = new ArrayList<>(List.of(properties.ffmpeg().ffmpegPath(), "-v", "error", "-y",
					"-i", input.toString(),
					"-map", "0:v:0", "-an", "-sn", "-dn",
					"-vf", "scale=w='min(%d,iw)':h='min(%d,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2"
							.formatted(properties.maxClipLongSide(), properties.maxClipShortSide()),
					"-fpsmax", String.valueOf(MAX_FPS),
					"-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
					"-maxrate", properties.ffmpeg().maxBitrateKbps() + "k",
					"-bufsize", 2 * properties.ffmpeg().maxBitrateKbps() + "k",
					"-pix_fmt", "yuv420p"));
			if (parts == 1) {
				command.addAll(List.of("-movflags", "+faststart", dir.resolve("part_000.mp4").toString()));
			}
			else {
				String boundaries = boundaries(durationSeconds, parts);
				command.addAll(List.of("-force_key_frames", boundaries,
						"-f", "segment", "-segment_times", boundaries, "-segment_time_delta", SEGMENT_TIME_DELTA,
						"-reset_timestamps", "1",
						"-segment_format", "mp4", "-segment_format_options", "movflags=+faststart",
						dir.resolve("part_%03d.mp4").toString()));
			}
			runOrFail(command, dir);
			return readParts(dir, "part_");
		});
	}

	/** Joins parts in list order; stream copy first, re-encode if the parts' encodings differ. */
	public byte[] concat(List<byte[]> parts) {
		if (parts.size() == 1) {
			return parts.getFirst();
		}
		return inWorkDir(dir -> {
			var listing = new StringBuilder();
			for (int i = 0; i < parts.size(); i++) {
				Path part = write(dir, "gray_%03d.mp4".formatted(i), parts.get(i));
				listing.append("file '").append(part.getFileName()).append("'\n");
			}
			Files.writeString(dir.resolve("list.txt"), listing);
			Path output = dir.resolve("joined.mp4");
			var base = List.of(properties.ffmpeg().ffmpegPath(), "-v", "error", "-y",
					"-f", "concat", "-safe", "0", "-i", "list.txt");
			if (!run(concat(base, "-c", "copy", "-movflags", "+faststart", output.toString()), dir, null)) {
				log.info("Stream-copy concat failed; re-encoding {} parts", parts.size());
				runOrFail(concat(base, "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
						"-pix_fmt", "yuv420p", "-movflags", "+faststart", output.toString()), dir);
			}
			return Files.readAllBytes(output);
		});
	}

	static int segmentCount(double durationSeconds, double segmentSeconds) {
		return Math.max(1, (int) Math.ceil(durationSeconds / segmentSeconds - 1e-9));
	}

	/** Equal-length cut points, e.g. 25s in 3 parts gives "8.333333,16.666667". */
	static String boundaries(double durationSeconds, int parts) {
		double length = durationSeconds / parts;
		return IntStream.range(1, parts)
				.mapToObj(i -> String.format(Locale.ROOT, "%.6f", i * length))
				.collect(Collectors.joining(","));
	}

	static Optional<VideoInfo> parseProbe(String output) {
		Map<String, String> values = new HashMap<>();
		output.lines().forEach(line -> {
			int eq = line.indexOf('=');
			if (eq > 0) {
				values.putIfAbsent(line.substring(0, eq), line.substring(eq + 1).strip());
			}
		});
		try {
			double duration = Double.parseDouble(values.get("duration"));
			int width = Integer.parseInt(values.get("width"));
			int height = Integer.parseInt(values.get("height"));
			double fps = parseRate(values.getOrDefault("avg_frame_rate", "0/0"));
			return duration > 0 && width > 0 && height > 0
					? Optional.of(new VideoInfo(duration, width, height, fps))
					: Optional.empty();
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	private static double parseRate(String rate) {
		String[] parts = rate.split("/");
		double numerator = Double.parseDouble(parts[0]);
		double denominator = parts.length > 1 ? Double.parseDouble(parts[1]) : 1;
		return denominator == 0 ? 0 : numerator / denominator;
	}

	private static List<String> concat(List<String> base, String... extra) {
		return Stream.concat(base.stream(), Stream.of(extra)).toList();
	}

	private static Path write(Path dir, String name, byte[] bytes) throws IOException {
		return Files.write(dir.resolve(name), bytes);
	}

	private static List<byte[]> readParts(Path dir, String prefix) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			var sorted = files.filter(p -> p.getFileName().toString().startsWith(prefix)).sorted().toList();
			var parts = new ArrayList<byte[]>(sorted.size());
			for (Path part : sorted) {
				parts.add(Files.readAllBytes(part));
			}
			if (parts.isEmpty()) {
				throw new VideoApiException(ErrorCode.PROCESSING_FAILED);
			}
			return parts;
		}
	}

	private void runOrFail(List<String> command, Path dir) throws IOException {
		if (!run(command, dir, null)) {
			throw new VideoApiException(ErrorCode.PROCESSING_FAILED);
		}
	}

	/** Returns false on a non-zero exit; stdout goes to {@code stdout} when given, else to the log file. */
	private boolean run(List<String> command, Path dir, Path stdout) throws IOException {
		Path logFile = dir.resolve("ffmpeg.log");
		var builder = new ProcessBuilder(command).directory(dir.toFile())
				.redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
				.redirectOutput(ProcessBuilder.Redirect.appendTo((stdout != null ? stdout : logFile).toFile()));
		Process process;
		try {
			process = builder.start();
		}
		catch (IOException ex) {
			available = false;
			throw new VideoApiException(ErrorCode.PROCESSOR_UNAVAILABLE);
		}
		try {
			if (!process.waitFor(properties.ffmpeg().timeout().toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				log.warn("{} timed out after {}", command.getFirst(), properties.ffmpeg().timeout());
				throw new VideoApiException(ErrorCode.PROCESSING_FAILED);
			}
		}
		catch (InterruptedException ex) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new VideoApiException(ErrorCode.PROCESSING_FAILED);
		}
		if (process.exitValue() != 0) {
			log.warn("{} exited {}: {}", command.getFirst(), process.exitValue(), tail(logFile));
			return false;
		}
		return true;
	}

	private static String tail(Path logFile) throws IOException {
		if (!Files.exists(logFile)) {
			return "";
		}
		String text = Files.readString(logFile, StandardCharsets.UTF_8).strip();
		return text.length() > 500 ? text.substring(text.length() - 500) : text;
	}

	private static boolean canRun(String binary) {
		try {
			Process process = new ProcessBuilder(binary, "-version")
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
		}
		catch (IOException ex) {
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private interface WorkDirTask<T> {
		T run(Path dir) throws IOException;
	}

	private static <T> T inWorkDir(WorkDirTask<T> task) {
		Path dir = null;
		try {
			dir = Files.createTempDirectory("video-");
			return task.run(dir);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			if (dir != null) {
				try {
					FileSystemUtils.deleteRecursively(dir);
				}
				catch (IOException ex) {
					log.warn("Could not delete {}", dir, ex);
				}
			}
		}
	}
}
