package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

public class VoiceInteractionTransactionTest {
	@Test
	public void asyncTargetedSearchMayClarifyOnlyItsOwnGenerationAndTarget() {
		VoiceInteractionTransaction transaction = new VoiceInteractionTransaction();
		transaction.begin(7L);
		VoiceIntent intent = VoiceIntentParser.parse("Play YouTube Numb", Locale.ENGLISH);
		transaction.onOutcome(7L, intent, VoiceCommandOutcome.pending(
				VoiceCommandOutcome.PlaybackEffect.REPLACE_PENDING));
		assertEquals(VoiceInteractionTransaction.State.RESOLVING, transaction.getState());
		assertFalse(transaction.beginClarification(6L, "youtube"));
		assertFalse(transaction.beginClarification(7L, "stremio"));
		assertTrue(transaction.beginClarification(7L, "youtube"));
		assertEquals(VoiceInteractionTransaction.State.CLARIFYING, transaction.getState());
	}

	@Test
	public void newerTransactionRejectsLateResultFromOlderSearch() {
		VoiceInteractionTransaction transaction = new VoiceInteractionTransaction();
		transaction.begin(1L);
		transaction.begin(2L);
		transaction.onOutcome(1L, VoiceIntentParser.parse("Play YouTube Old", Locale.ENGLISH),
				VoiceCommandOutcome.pending(VoiceCommandOutcome.PlaybackEffect.REPLACE_PENDING));
		assertEquals(VoiceInteractionTransaction.State.LISTENING, transaction.getState());
		assertFalse(transaction.beginClarification(1L, "youtube"));
	}

	@Test
	public void completionAndCancellationCloseTheGeneration() {
		VoiceInteractionTransaction transaction = new VoiceInteractionTransaction();
		transaction.begin(3L);
		transaction.complete(3L);
		assertFalse(transaction.isCurrent(3L));
		assertEquals(VoiceInteractionTransaction.State.COMPLETED, transaction.getState());
		transaction.begin(4L);
		transaction.cancel();
		assertFalse(transaction.isActive());
		assertEquals(VoiceInteractionTransaction.State.CANCELLED, transaction.getState());
	}
}
