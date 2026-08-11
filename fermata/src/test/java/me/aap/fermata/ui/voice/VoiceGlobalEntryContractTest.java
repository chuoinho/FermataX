package me.aap.fermata.ui.voice;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class VoiceGlobalEntryContractTest {
	@Test
	public void sharedMicrophonesUseGlobalEntryWhileChatKeepsItsContextualEntry()
			throws IOException {
		assertTrue(source("fermata/src/main/java/me/aap/fermata/ui/fragment/ToolBarMediator.java")
				.contains("startGlobalVoiceControl()"));
		assertTrue(source("fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java")
				.contains("startGlobalVoiceControl()"));
		assertTrue(source("fermata/src/main/java/me/aap/fermata/ui/fragment/FloatingButtonMediator.java")
				.contains("startGlobalVoiceControl()"));
		assertTrue(source("modules/chat/src/main/java/me/aap/fermata/addon/chat/ChatFragment.java")
				.contains("public boolean startVoiceAssistant()"));
	}

	private static String source(String relative) throws IOException {
		Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while ((root != null) && !Files.exists(root.resolve("settings.gradle"))) {
			root = root.getParent();
		}
		if (root == null) throw new IOException("Project root not found");
		return new String(Files.readAllBytes(root.resolve(relative)), UTF_8);
	}
}
