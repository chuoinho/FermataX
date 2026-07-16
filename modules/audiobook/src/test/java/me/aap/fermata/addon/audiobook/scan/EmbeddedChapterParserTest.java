package me.aap.fermata.addon.audiobook.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import me.aap.fermata.addon.audiobook.scan.EmbeddedChapterParser.ChapterMark;
import me.aap.fermata.addon.audiobook.scan.EmbeddedChapterParser.EmbeddedChapter;

public class EmbeddedChapterParserTest {
	@Test
	public void parsesXiphChapterTime() {
		assertEquals(6_852_800, EmbeddedChapterParser.parseVorbisTime("01:54:12.8"));
		assertEquals(10_125, EmbeddedChapterParser.parseVorbisTime("00:00:10.125"));
		assertEquals(-1, EmbeddedChapterParser.parseVorbisTime("00:61:00"));
		assertEquals(-1, EmbeddedChapterParser.parseVorbisTime("not-a-time"));
	}

	@Test
	public void normalizesAndBoundsSegments() {
		List<EmbeddedChapter> chapters = EmbeddedChapterParser.normalize(List.of(
				new ChapterMark(10_000, "Second"),
				new ChapterMark(0, ""),
				new ChapterMark(10_000, "Duplicate"),
				new ChapterMark(30_000, "Out of range")), 20_000);

		assertEquals(2, chapters.size());
		assertEquals("Chapter 1", chapters.get(0).title());
		assertEquals(0, chapters.get(0).offsetMs());
		assertEquals(10_000, chapters.get(0).durationMs());
		assertEquals("Second", chapters.get(1).title());
		assertTrue(chapters.get(1).durationMs() > 0);
	}
}
