package me.aap.fermata.action;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class HardwareInputContractTest {
	@Test
	public void compatibilityAliasesAreExplicitAndVoiceAssistIsNotCaptured() throws Exception {
		String keys = source("Key.java");

		assertTrue(keys.contains("KEYCODE_MEDIA_SKIP_FORWARD"));
		assertTrue(keys.contains("KEYCODE_MEDIA_SKIP_BACKWARD"));
		assertTrue(keys.contains("CHANNEL_UP(KeyEvent.KEYCODE_CHANNEL_UP, Action.NONE)"));
		assertTrue(keys.contains("CHANNEL_DOWN(KeyEvent.KEYCODE_CHANNEL_DOWN, Action.NONE)"));
		assertFalse(keys.contains("KEYCODE_VOICE_ASSIST, Action"));
		assertFalse(keys.contains("KEYCODE_ASSIST, Action"));
	}

	@Test
	public void legacyFacadeHasNoProcessWideGestureWorker() throws Exception {
		String handler = source("KeyEventHandler.java");

		assertTrue(handler.contains("getHardwareInputRouter()"));
		assertFalse(handler.contains("static Worker"));
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/action").resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/action").resolve(name);
		}
		return new String(Files.readAllBytes(file), UTF_8);
	}
}
