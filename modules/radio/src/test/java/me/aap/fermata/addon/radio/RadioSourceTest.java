package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RadioSourceTest {
	@Test
	public void acceptsHttpAndHttpsStreams() {
		assertTrue(RadioSource.restore("one", "Station", "http://example.com/live.mp3").isValid());
		assertTrue(RadioSource.restore("two", "Station", "https://example.com/live.m3u8").isValid());
	}

	@Test
	public void rejectsIncompleteOrUnsupportedSources() {
		assertFalse(RadioSource.restore("one", "", "https://example.com/live").isValid());
		assertFalse(RadioSource.restore("one", "Station", "example.com/live").isValid());
		assertFalse(RadioSource.restore("one", "Station", "ftp://example.com/live").isValid());
	}

	@Test
	public void updateKeepsStableSourceId() {
		RadioSource source = RadioSource.restore("stable-id", "Old", "https://example.com/old");
		RadioSource updated = source.update("New", "https://example.com/new");
		assertEquals("stable-id", updated.getId());
		assertEquals("New", updated.getName());
		assertEquals("https://example.com/new", updated.getUrl());
	}
}
