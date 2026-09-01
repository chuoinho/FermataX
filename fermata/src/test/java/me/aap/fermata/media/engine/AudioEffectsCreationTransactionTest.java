package me.aap.fermata.media.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AudioEffectsCreationTransactionTest {
	@Test
	public void rollbackReleasesEveryAcquiredResourceInReverseOrder() {
		List<String> released = new ArrayList<>();
		AudioEffects.CreationTransaction transaction = new AudioEffects.CreationTransaction();
		transaction.acquire(() -> "equalizer", released::add);
		transaction.acquire(() -> "bass", released::add);
		transaction.rollback();

		assertEquals(List.of("bass", "equalizer"), released);
	}

	@Test
	public void committedTransactionDoesNotReleaseOwnedResources() {
		List<String> released = new ArrayList<>();
		AudioEffects.CreationTransaction transaction = new AudioEffects.CreationTransaction();
		transaction.acquire(() -> "equalizer", released::add);
		transaction.commit();
		transaction.rollback();

		assertTrue(released.isEmpty());
	}
}
