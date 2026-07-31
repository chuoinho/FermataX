package me.aap.fermata.architecture;

import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ArchitectureBoundaryTest {
	private static final Map<String, String> ADDON_PACKAGES = Map.of(
			"audiobook", "me.aap.fermata.addon.audiobook.",
			"cast", "me.aap.fermata.addon.cast.",
			"chat", "me.aap.fermata.addon.chat.",
			"podcast", "me.aap.fermata.addon.podcast.",
			"radio", "me.aap.fermata.addon.radio.",
			"stremio", "me.aap.fermata.addon.stremio.",
			"tv", "me.aap.fermata.addon.tv.",
			"web", "me.aap.fermata.addon.web.");

	@Test
	public void coreDoesNotImportConcreteAddonImplementations() throws IOException {
		Path root = projectRoot();
		assertNoForbiddenImports(root.resolve("fermata/src/main"), null);
		assertNoForbiddenImports(root.resolve("fermata/src/auto"), null);
	}

	@Test
	public void contentAddonsDoNotImportSiblingImplementations() throws IOException {
		Path root = projectRoot();
		for (Map.Entry<String, String> addon : ADDON_PACKAGES.entrySet()) {
			Path source = root.resolve("modules").resolve(addon.getKey()).resolve("src/main");
			if (Files.isDirectory(source)) assertNoForbiddenImports(source, addon.getValue());
		}
	}

	@Test
	public void runtimeIdentityRemainsExplicitAndStable() throws IOException {
		String gradle = new String(Files.readAllBytes(
				projectRoot().resolve("fermata/build.gradle")), UTF_8);
		assertTrue(gradle.contains("def defaultApplicationId = 'me.app.fermataX'"));
		assertTrue(gradle.contains("namespace = 'me.aap.fermata'"));
		assertTrue(gradle.contains("applicationId configuredApplicationId"));
	}

	@Test
	public void hotspotClassesCannotGrowPastTheRecordedBaseline() throws IOException {
		Map<String, Long> limits = new LinkedHashMap<>();
		limits.put("fermata/src/main/java/me/aap/fermata/media/service/MediaSessionCallback.java",
				2229L);
		limits.put("fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java",
				1291L);
		limits.put("fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelView.java", 956L);
		limits.put("modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebView.java",
				1248L);
		limits.put("modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeMediaEngine.java",
				1155L);

		Path root = projectRoot();
		for (Map.Entry<String, Long> entry : limits.entrySet()) {
			long lines;
			try (var content = Files.lines(root.resolve(entry.getKey()))) {
				lines = content.filter(line -> !line.isBlank()).count();
			}
			assertTrue(entry.getKey() + " grew to " + lines + " nonblank lines",
					lines <= entry.getValue());
		}
	}

	@Test
	public void playbackSnapshotPrecedesSynchronousMediaControllerCallbacks() throws IOException {
		String source = new String(Files.readAllBytes(projectRoot().resolve(
				"fermata/src/main/java/me/aap/fermata/media/service/MediaSessionCallback.java")), UTF_8);
		assertOrder(source, "private void setSessionMetadata(",
				"updatePlaybackSnapshot(", "session.setMetadata(metadata)");
		assertOrder(source,
				"private void setPlaybackState(PlaybackStateCompat state, @Nullable PlayableItem item,",
				"updatePlaybackSnapshot(state, metadata, item)", "session.setPlaybackState(state)");
	}

	private static void assertOrder(String source, String method, String publication,
			String callback) {
		int methodIndex = source.indexOf(method);
		int publicationIndex = source.indexOf(publication, methodIndex);
		int callbackIndex = source.indexOf(callback, methodIndex);
		assertTrue("Missing method marker: " + method, methodIndex >= 0);
		assertTrue("Snapshot publication must precede callback in " + method,
				(publicationIndex > methodIndex) && (callbackIndex > publicationIndex));
	}

	private static void assertNoForbiddenImports(Path source, String allowedPrefix)
			throws IOException {
		if (!Files.isDirectory(source)) return;
		try (var files = Files.walk(source)) {
			List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
			for (Path file : javaFiles) {
				for (String line : Files.readAllLines(file)) {
					String value = line.trim();
					if (!value.startsWith("import ")) continue;
					for (String forbidden : ADDON_PACKAGES.values()) {
						if (forbidden.equals(allowedPrefix)) continue;
						assertTrue(file + " imports sibling addon implementation: " + value,
								!value.startsWith("import " + forbidden));
					}
				}
			}
		}
	}

	private static Path projectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("settings.gradle")) &&
					Files.isDirectory(current.resolve("fermata"))) return current;
			current = current.getParent();
		}
		throw new IllegalStateException("Unable to locate FermataX project root");
	}
}
