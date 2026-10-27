package com.commure.backend.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.util.FileSystemUtils;

/** Generates real H.264 videos and inspects outputs with ffmpeg; tests using it need ffmpeg on PATH. */
final class FfmpegFixtures {

	record Section(String color, double seconds) {
	}

	record Probe(double durationSeconds, long frames, int width, int height, double fps, String codec) {
	}

	private static Boolean available;

	private FfmpegFixtures() {
	}

	/** For {@code @EnabledIf}. */
	static boolean available() {
		if (available == null) {
			available = new VideoProcessor(VideoFixtures.properties()).isAvailable();
		}
		return available;
	}

	/** Solid colour sections in order, e.g. red 10s then green 10s then blue 5s. */
	static byte[] colorSections(String size, String fps, Section... sections) {
		var graph = new StringBuilder();
		var labels = new StringBuilder();
		for (int i = 0; i < sections.length; i++) {
			graph.append(String.format(Locale.ROOT, "color=c=%s:s=%s:r=%s:d=%.3f[s%d];",
					sections[i].color(), size, fps, sections[i].seconds(), i));
			labels.append("[s").append(i).append(']');
		}
		graph.append(labels).append("concat=n=").append(sections.length).append(":v=1:a=0[v]");
		return encode(List.of("-filter_complex", graph.toString(), "-map", "[v]"));
	}

	/** Noisy test pattern: worst case for bitrate, to prove parts stay under the 3 MB limit. */
	static byte[] noise(String size, String fps, double seconds) {
		return encode(List.of("-f", "lavfi", "-i",
				String.format(Locale.ROOT, "testsrc2=s=%s:r=%s:d=%.3f,noise=alls=80:allf=t", size, fps, seconds),
				"-b:v", "10M", "-maxrate", "10M", "-bufsize", "10M"));
	}

	/** Dominant RGB channel of the frame at {@code seconds}: 0 red, 1 green, 2 blue. */
	static int dominantChannelAt(byte[] video, double seconds) {
		return inTemp(dir -> {
			Path input = Files.write(dir.resolve("in.mp4"), video);
			Path raw = dir.resolve("pixel.rgb");
			run(dir, List.of("ffmpeg", "-v", "error", "-ss", String.format(Locale.ROOT, "%.3f", seconds),
					"-i", input.toString(), "-frames:v", "1", "-vf", "scale=1:1", "-f", "rawvideo",
					"-pix_fmt", "rgb24", raw.toString()));
			byte[] rgb = Files.readAllBytes(raw);
			int best = 0;
			for (int c = 1; c < 3; c++) {
				if ((rgb[c] & 0xff) > (rgb[best] & 0xff)) {
					best = c;
				}
			}
			return best;
		});
	}

	static Probe probe(byte[] video) {
		return inTemp(dir -> {
			Path input = Files.write(dir.resolve("in.mp4"), video);
			String out = run(dir, List.of("ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
					"-show_entries", "stream=nb_read_frames,width,height,codec_name,avg_frame_rate:format=duration",
					"-of", "default=noprint_wrappers=1", input.toString()));
			Map<String, String> values = new HashMap<>();
			out.lines().forEach(line -> {
				int eq = line.indexOf('=');
				if (eq > 0) {
					values.putIfAbsent(line.substring(0, eq), line.substring(eq + 1).strip());
				}
			});
			String[] rate = values.get("avg_frame_rate").split("/");
			return new Probe(Double.parseDouble(values.get("duration")),
					Long.parseLong(values.get("nb_read_frames")),
					Integer.parseInt(values.get("width")),
					Integer.parseInt(values.get("height")),
					Double.parseDouble(rate[0]) / Double.parseDouble(rate[1]),
					values.get("codec_name"));
		});
	}

	private static byte[] encode(List<String> source) {
		return inTemp(dir -> {
			var command = new ArrayList<>(List.of("ffmpeg", "-v", "error", "-y"));
			command.addAll(source);
			if (!source.contains("-crf")) {
				command.addAll(List.of("-crf", "23"));
			}
			command.addAll(List.of("-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
					dir.resolve("out.mp4").toString()));
			run(dir, command);
			return Files.readAllBytes(dir.resolve("out.mp4"));
		});
	}

	private static String run(Path dir, List<String> command) throws IOException {
		Path log = dir.resolve("out.log");
		Process process = new ProcessBuilder(command).directory(dir.toFile())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
		try {
			if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0) {
				throw new IllegalStateException(command.getFirst() + " failed: " + Files.readString(log));
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
		return Files.readString(log);
	}

	private interface Task<T> {
		T run(Path dir) throws IOException;
	}

	private static <T> T inTemp(Task<T> task) {
		Path dir = null;
		try {
			dir = Files.createTempDirectory("ffmpeg-fixture-");
			return task.run(dir);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		finally {
			if (dir != null) {
				try {
					FileSystemUtils.deleteRecursively(dir);
				}
				catch (IOException ignored) {
					// Best-effort cleanup of a temp directory.
				}
			}
		}
	}
}
