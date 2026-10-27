package com.commure.backend.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.commure.backend.video.FfmpegFixtures.Section;

@EnabledIf("com.commure.backend.video.FfmpegFixtures#available")
class VideoProcessorTest {

	private static final int RED = 0;
	private static final int GREEN = 1;
	private static final int BLUE = 2;

	private static byte[] rgb25s;

	private final VideoProcessor processor = new VideoProcessor(VideoFixtures.properties());

	@BeforeAll
	static void fixtures() {
		rgb25s = FfmpegFixtures.colorSections("320x240", "30000/1001",
				new Section("red", 10), new Section("green", 10), new Section("blue", 5));
	}

	@Test
	void probeReadsDurationResolutionAndFrameRate() {
		var info = processor.probe(rgb25s).orElseThrow();

		assertThat(info.durationSeconds()).isCloseTo(25, within(0.1));
		assertThat(info.width()).isEqualTo(320);
		assertThat(info.height()).isEqualTo(240);
		assertThat(info.fps()).isCloseTo(29.97, within(0.01));
	}

	@Test
	void probeOfNonVideoIsEmpty() {
		assertThat(processor.probe(VideoFixtures.mp4Bytes(4096))).isEmpty();
	}

	@Test
	void splitsIntoOrderedPartsWithinApiLimits() {
		List<byte[]> parts = processor.split(rgb25s, 25);

		assertThat(parts).hasSize(3);
		long totalFrames = 0;
		for (byte[] part : parts) {
			var probe = FfmpegFixtures.probe(part);
			assertThat(probe.codec()).isEqualTo("h264");
			assertThat(probe.durationSeconds()).isLessThanOrEqualTo(10.0);
			assertThat(probe.frames()).isLessThanOrEqualTo(600);
			assertThat(part.length).isLessThanOrEqualTo(3_000_000);
			totalFrames += probe.frames();
		}
		assertThat(totalFrames).isCloseTo(749, within(2L));
		assertThat(FfmpegFixtures.dominantChannelAt(parts.get(0), 1)).isEqualTo(RED);
		assertThat(FfmpegFixtures.dominantChannelAt(parts.get(1), 7)).isEqualTo(GREEN);
		assertThat(FfmpegFixtures.dominantChannelAt(parts.get(2), 7)).isEqualTo(BLUE);
	}

	@Test
	void spreadsBoundariesEvenlyInsteadOfLeavingATinyTail() {
		byte[] video = FfmpegFixtures.colorSections("160x120", "30000/1001", new Section("gray", 30));

		List<byte[]> parts = processor.split(video, 30);

		assertThat(parts).hasSize(4);
		for (byte[] part : parts) {
			assertThat(FfmpegFixtures.probe(part).durationSeconds()).isCloseTo(7.5, within(0.1));
		}
	}

	@Test
	void normalizesHighResolutionAndHighFrameRateInput() {
		byte[] video = FfmpegFixtures.colorSections("2560x1440", "90", new Section("red", 2));

		List<byte[]> parts = processor.split(video, 2);

		assertThat(parts).hasSize(1);
		var probe = FfmpegFixtures.probe(parts.getFirst());
		assertThat(probe.width()).isLessThanOrEqualTo(1920);
		assertThat(probe.height()).isLessThanOrEqualTo(1080);
		assertThat(probe.fps()).isLessThanOrEqualTo(60.0);
		assertThat(probe.frames()).isLessThanOrEqualTo(600);
	}

	@Test
	void capsBitrateSoNoisyFullHdPartsStayUnderThreeMegabytes() {
		byte[] video = FfmpegFixtures.noise("1920x1080", "30", 12);
		assertThat(video.length).as("fixture should be far over the clip limit").isGreaterThan(3_000_000);

		List<byte[]> parts = processor.split(video, 12);

		assertThat(parts).hasSize(2).allSatisfy(part -> assertThat(part.length).isLessThanOrEqualTo(3_000_000));
	}

	@Test
	void concatJoinsPartsInListOrder() {
		List<byte[]> parts = processor.split(rgb25s, 25);

		byte[] joined = processor.concat(parts);

		assertThat(FfmpegFixtures.probe(joined).durationSeconds()).isCloseTo(25, within(0.2));
		assertThat(FfmpegFixtures.dominantChannelAt(joined, 5)).isEqualTo(RED);
		assertThat(FfmpegFixtures.dominantChannelAt(joined, 15)).isEqualTo(GREEN);
		assertThat(FfmpegFixtures.dominantChannelAt(joined, 22)).isEqualTo(BLUE);
	}

	@Test
	void concatOfReversedPartsReversesTheVideo() {
		List<byte[]> parts = processor.split(rgb25s, 25);

		byte[] joined = processor.concat(List.of(parts.get(2), parts.get(1), parts.get(0)));

		assertThat(FfmpegFixtures.dominantChannelAt(joined, 7)).isEqualTo(BLUE);
		assertThat(FfmpegFixtures.dominantChannelAt(joined, 23)).isEqualTo(RED);
	}

	@Test
	void segmentMathAndBoundaries() {
		assertThat(VideoProcessor.segmentCount(8, 9.9)).isEqualTo(1);
		assertThat(VideoProcessor.segmentCount(9.9, 9.9)).isEqualTo(1);
		assertThat(VideoProcessor.segmentCount(10, 9.9)).isEqualTo(2);
		assertThat(VideoProcessor.segmentCount(120, 9.9)).isEqualTo(13);
		assertThat(VideoProcessor.boundaries(25, 3)).isEqualTo("8.333333,16.666667");
	}

	@Test
	void parsesProbeOutput() {
		var info = VideoProcessor.parseProbe("width=854\nheight=480\navg_frame_rate=24/1\nduration=8.000000\n");

		assertThat(info).contains(new VideoInfo(8, 854, 480, 24));
		assertThat(VideoProcessor.parseProbe("duration=N/A")).isEmpty();
	}
}
