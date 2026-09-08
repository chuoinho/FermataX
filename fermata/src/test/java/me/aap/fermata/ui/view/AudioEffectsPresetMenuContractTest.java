package me.aap.fermata.ui.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class AudioEffectsPresetMenuContractTest {
	@Test
	public void presetSelectorUsesTheFermataOverlayInsteadOfAFrameworkSpinner() throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path source = root.resolve("src/main/java/me/aap/fermata/ui/view/AudioEffectsScreenView.java");
		if (!Files.isRegularFile(source)) {
			source = root.resolve("fermata/src/main/java/me/aap/fermata/ui/view/AudioEffectsScreenView.java");
		}
		String view = new String(Files.readAllBytes(source), UTF_8);

		assertFalse(view.contains("new Spinner("));
		assertFalse(view.contains("class PresetAdapter"));
		assertTrue(view.contains("getContextMenu().show"));
	}
}
