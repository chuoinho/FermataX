package me.aap.fermata.whisper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

public class WhisperAudioInputTest {
	@Test
	public void acceptsWholeFramesFromDirectBuffersAndPreservesSliceSemantics() {
		ByteBuffer full = ByteBuffer.allocateDirect(8);
		assertSame(full, WhisperAudioInput.prepare(full, 1, 2, 1, 16_000));

		ByteBuffer partial = ByteBuffer.allocateDirect(12);
		partial.position(2);
		partial.limit(10);
		ByteBuffer slice = WhisperAudioInput.prepare(partial, 1, 2, 2, 16_000);
		assertEquals(0, slice.position());
		assertEquals(8, slice.remaining());
	}

	@Test
	public void rejectsMalformedAudioBeforeNativeCodeCanReadIt() {
		assertThrows(IllegalArgumentException.class,
				() -> WhisperAudioInput.prepare(ByteBuffer.allocate(8), 1, 2, 1, 16_000));
		assertThrows(IllegalArgumentException.class,
				() -> WhisperAudioInput.prepare(ByteBuffer.allocateDirect(7), 1, 2, 1, 16_000));
		assertThrows(IllegalArgumentException.class,
				() -> WhisperAudioInput.prepare(ByteBuffer.allocateDirect(8), 0, 2, 1, 16_000));
		assertThrows(IllegalArgumentException.class,
				() -> WhisperAudioInput.prepare(ByteBuffer.allocateDirect(8), 1, 5, 1, 16_000));
		assertThrows(IllegalArgumentException.class,
				() -> WhisperAudioInput.prepare(ByteBuffer.allocateDirect(8), 1, 2, 0, 16_000));
		assertThrows(IllegalArgumentException.class,
				() -> WhisperAudioInput.prepare(ByteBuffer.allocateDirect(8), 1, 2, 9, 16_000));
		assertThrows(IllegalArgumentException.class,
				() -> WhisperAudioInput.prepare(ByteBuffer.allocateDirect(8), 1, 2, 1, 0));
	}

	@Test
	public void appliesOnlyPlausibleNativeConsumedCounts() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(10);
		assertFalse(WhisperAudioInput.applyNativeConsumed(buffer, 10, 6));
		assertEquals(6, buffer.position());
		assertTrue(WhisperAudioInput.applyNativeConsumed(buffer, 4, -4));
		assertEquals(10, buffer.position());

		assertThrows(IllegalStateException.class,
				() -> WhisperAudioInput.applyNativeConsumed(ByteBuffer.allocateDirect(4), 4, 5));
		assertThrows(IllegalStateException.class,
				() -> WhisperAudioInput.applyNativeConsumed(ByteBuffer.allocateDirect(4), 4,
						Integer.MIN_VALUE));
	}
}
