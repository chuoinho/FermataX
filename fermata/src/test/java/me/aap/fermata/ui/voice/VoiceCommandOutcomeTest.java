package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoiceCommandOutcomeTest {
	@Test
	public void explicitPausePreventsRecognitionCleanupFromRestoringPlayback() {
		VoiceCommandOutcome pause = VoiceCommandOutcome.completed(
				VoiceCommandOutcome.PlaybackEffect.KEEP_PAUSED);
		assertTrue(pause.isHandled());
		assertTrue(pause.shouldKeepPaused());
		assertFalse(VoiceCommandOutcome.pending(
				VoiceCommandOutcome.PlaybackEffect.REPLACE_PENDING).shouldKeepPaused());
		assertFalse(VoiceCommandOutcome.unhandled().isHandled());
	}
}
