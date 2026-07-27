package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DeferredInitialSeekTest {
	@Test
	public void p2pStartsAtZeroThenConsumesResumeExactlyOnce() {
		DeferredInitialSeek seek = new DeferredInitialSeek();
		Object engine = new Object();
		Object item = new Object();
		assertEquals(0L, seek.prepare(engine, item, 7L, 6_281_268L, true));
		assertEquals(6_281_268L, seek.consume(engine, item, 7L));
		assertEquals(-1L, seek.consume(engine, item, 7L));
	}

	@Test
	public void directPlaybackKeepsOriginalPosition() {
		DeferredInitialSeek seek = new DeferredInitialSeek();
		assertEquals(1_500L, seek.prepare(new Object(), new Object(), 1L, 1_500L, false));
	}

	@Test
	public void replacedPlaybackRejectsStaleFirstFrame() {
		DeferredInitialSeek seek = new DeferredInitialSeek();
		Object oldEngine = new Object();
		Object oldItem = new Object();
		seek.prepare(oldEngine, oldItem, 1L, 5_000L, true);
		Object currentEngine = new Object();
		Object currentItem = new Object();
		seek.prepare(currentEngine, currentItem, 2L, 8_000L, true);
		assertEquals(-1L, seek.consume(oldEngine, oldItem, 1L));
		assertEquals(8_000L, seek.consume(currentEngine, currentItem, 2L));
	}
}
