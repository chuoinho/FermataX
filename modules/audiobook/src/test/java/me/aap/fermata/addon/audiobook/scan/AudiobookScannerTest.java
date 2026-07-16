package me.aap.fermata.addon.audiobook.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AudiobookScannerTest {
	@Test
	public void recognizesSupportedAudioExtensionsCaseInsensitively() {
		assertTrue(AudiobookScanner.isAudio("book.M4B"));
		assertTrue(AudiobookScanner.isAudio("chapter 01.mp3"));
		assertTrue(AudiobookScanner.isAudio("chapter.opus"));
		assertFalse(AudiobookScanner.isAudio("cover.jpg"));
		assertFalse(AudiobookScanner.isAudio("book.epub"));
	}

	@Test
	public void mapsPlaybackMimeTypes() {
		assertEquals("audio/mp4", AudiobookScanner.mimeType("book.m4b"));
		assertEquals("audio/mpeg", AudiobookScanner.mimeType("chapter.mp3"));
		assertEquals("audio/ogg", AudiobookScanner.mimeType("chapter.opus"));
	}
}
