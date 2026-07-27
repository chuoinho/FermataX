package me.aap.fermata.addon.stremio.item;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioSubtitleSelectionStoreTest {
	@Test
	public void disabledChoiceIsScopedToOneVideoAndCanReturnToAutomatic() {
		String first = "stremio:video:first";
		String second = "stremio:video:second";

		StremioSubtitleSelectionStore.disable(first);

		assertTrue(StremioSubtitleSelectionStore.get(first).disabled());
		assertNull(StremioSubtitleSelectionStore.get(second));
		StremioSubtitleSelectionStore.useDefault(first);
		assertNull(StremioSubtitleSelectionStore.get(first));
	}
}
