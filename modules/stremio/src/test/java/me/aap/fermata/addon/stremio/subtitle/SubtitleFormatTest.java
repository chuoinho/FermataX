package me.aap.fermata.addon.stremio.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.junit.Test;

public class SubtitleFormatTest {
	@Test
	public void classifiesSupportedFormatsFromHintOrSafePath() {
		assertEquals(SubtitleFormat.SUBRIP, SubtitleFormat.classify(
				URI.create("https://example.invalid/subtitle?id=1"), "application/x-subrip"));
		assertEquals(SubtitleFormat.WEBVTT, SubtitleFormat.classify(
				URI.create("https://example.invalid/movie.VTT?token=secret"), null));
		assertTrue(SubtitleFormat.ASS.isSupported());
		assertTrue(SubtitleFormat.TTML.isSupported());
	}

	@Test
	public void unsupportedAndUnknownFormatsRemainExplicit() {
		assertEquals(SubtitleFormat.MICRODVD, SubtitleFormat.classify(
				URI.create("https://example.invalid/movie.sub"), null));
		assertFalse(SubtitleFormat.MICRODVD.isSupported());
		assertEquals(SubtitleFormat.UNKNOWN, SubtitleFormat.classify(
				URI.create("https://example.invalid/subtitle"), null));
	}

	@Test
	public void extensionlessHttpDownloadUsesTextSubtitleFallback() {
		assertTrue(SubtitleFormat.UNKNOWN.isEngineReadable(
				URI.create("https://subs.example.invalid/download/file/123")));
		assertFalse(SubtitleFormat.UNKNOWN.isEngineReadable(
				URI.create("https://subs.example.invalid/download/file.txt")));
		assertFalse(SubtitleFormat.ASS.isEngineReadable(
				URI.create("https://subs.example.invalid/download/file")));
	}
}
