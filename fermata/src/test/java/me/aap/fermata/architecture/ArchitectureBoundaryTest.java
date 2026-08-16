package me.aap.fermata.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class ArchitectureBoundaryTest {
	private static final Pattern PROJECT_DEPENDENCY = Pattern.compile(
			"project\\s*\\(\\s*(?:path\\s*:\\s*)?['\"]:([^'\"]+)['\"]");
	private static final Map<String, String> ADDON_PACKAGES = Map.ofEntries(
			Map.entry("audiobook", "me.aap.fermata.addon.audiobook."),
			Map.entry("cast", "me.aap.fermata.addon.cast."),
			Map.entry("chat", "me.aap.fermata.addon.chat."),
			Map.entry("exoplayer", "me.aap.fermata.engine.exoplayer."),
			Map.entry("gdrive", "me.aap.fermata.vfs.gdrive."),
			Map.entry("mlkit", "me.aap.fermata.mlkit."),
			Map.entry("opusmt", "me.aap.fermata.opusmt."),
			Map.entry("podcast", "me.aap.fermata.addon.podcast."),
			Map.entry("radio", "me.aap.fermata.addon.radio."),
			Map.entry("sftp", "me.aap.fermata.vfs.sftp."),
			Map.entry("smb", "me.aap.fermata.vfs.smb."),
			Map.entry("stremio", "me.aap.fermata.addon.stremio."),
			Map.entry("tv", "me.aap.fermata.addon.tv."),
			Map.entry("vlc", "me.aap.fermata.engine.vlc."),
			Map.entry("web", "me.aap.fermata.addon.web."),
			Map.entry("whisper", "me.aap.fermata.whisper."));

	@Test
	public void coreDoesNotImportConcreteAddonImplementations() throws IOException {
		Path root = projectRoot();
		assertNoForbiddenImports(root.resolve("fermata/src/main"), null);
		assertNoForbiddenImports(root.resolve("fermata/src/auto"), null);
	}

	@Test
	public void coreUiDoesNotReferenceConcreteAddonFragmentTypes() throws IOException {
		Path root = projectRoot();
		List<String> forbidden = List.of("TvFragment", "YoutubeFragment", "WebBrowserFragment");
		assertNoSourceReferences(root, root.resolve("fermata/src/main/java/me/aap/fermata/ui"), forbidden);
		assertNoSourceReferences(root, root.resolve("fermata/src/auto/java/me/aap/fermata/ui"), forbidden);
	}

	@Test
	public void webAddonUsesCommonUiShellAuthorities() throws IOException {
		String fragment = source("modules/web/src/main/java/me/aap/fermata/addon/web/WebBrowserFragment.java");
		String toolbar = source("modules/web/src/main/java/me/aap/fermata/addon/web/WebToolBarMediator.java");
		String youtube = source("modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeFragment.java");

		assertTrue(fragment.contains("WebBackNavigationPolicy.resolve(fullScreen, v.canGoBack())"));
		assertTrue(fragment.contains("case EXIT_FULLSCREEN -> v.exitFullScreenForBack()"));
		assertTrue(fragment.contains("case WEB_HISTORY ->"));
		assertTrue(toolbar.contains("TopBarMediatorSupport.installBackButton(tb, this)"));
		assertFalse(toolbar.contains("addButton(tb, me.aap.utils.R.drawable.back"));
		assertTrue(toolbar.contains("TopBarController.refresh(MainActivityDelegate.get(tb.getContext()), f)"));
		assertTrue(toolbar.contains("TopBarController.refresh(MainActivityDelegate.get(tb.getContext()))"));
		assertFalse(toolbar.contains("tool_bar_back_button).setVisibility"));
		assertTrue(youtube.contains("TopBarPlaybackContext"));
		assertTrue(youtube.contains("YoutubeToolbarPolicy.usePlaybackTitle("));
		assertTrue(youtube.contains("TopBarController.refresh(a, f)"));
		assertFalse(youtube.contains("tb.setTitle("));
		assertFalse(youtube.contains("getToolBar().setTitle("));
	}

	@Test
	public void tvAddonUsesCommonUiShellAuthorities() throws IOException {
		String tv = source("modules/tv/src/main/java/me/aap/fermata/addon/tv/TvFragment.java");
		String media = source("fermata/src/main/java/me/aap/fermata/ui/fragment/MediaLibFragment.java");

		assertTrue(tv.contains("public class TvFragment extends MediaLibFragment"));
		assertTrue(tv.contains("return me.aap.fermata.R.id.tv_fragment;"));
		assertFalse(tv.contains("getToolBarMediator()"));
		assertFalse(tv.contains("public boolean onBackPressed()"));
		assertTrue(tv.contains("public void navBarItemReselected(int itemId)"));
		assertTrue(tv.contains("getAdapter().setParent(getRootItem())"));
		assertFalse(tv.contains("setVideoMode("));
		assertFalse(tv.contains("BodyLayout"));
		assertFalse(tv.contains("TopBarController"));
		assertFalse(tv.contains("BackNavigationPolicy"));
		assertTrue(media.contains("return ToolBarMediator.instance;"));
		assertTrue(media.contains("if (BackNavigationPolicy.leaveVideoMode(ad)) return true;"));
	}

	@Test
	public void addonModulesDoNotImportSiblingImplementations() throws IOException {
		Path root = projectRoot();
		Path modules = root.resolve("modules");
		Set<String> discovered;
		try (var directories = Files.list(modules)) {
			discovered = directories.filter(Files::isDirectory)
					.map(path -> path.getFileName().toString())
					.collect(java.util.stream.Collectors.toCollection(TreeSet::new));
		}
		Set<String> mapped = new TreeSet<>(ADDON_PACKAGES.keySet());
		if (!mapped.equals(discovered)) {
			fail("Addon package ownership map must match modules/ directories; mapped=" + mapped +
					", discovered=" + discovered);
		}

		List<String> violations = new ArrayList<>();
		for (Map.Entry<String, String> addon : ADDON_PACKAGES.entrySet()) {
			Path module = modules.resolve(addon.getKey());
			assertNoForbiddenImports(root, module.resolve("src"), addon.getKey(),
					addon.getValue(), violations);
			assertNoSiblingProjectDependencies(root, module.resolve("build.gradle"),
					addon.getKey(), violations);
		}
		if (!violations.isEmpty()) {
			fail("Cross-addon dependencies are forbidden. Move shared contracts into :fermata or " +
					":utils:\n" + String.join("\n", violations));
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

	private static String source(String relativePath) throws IOException {
		return new String(Files.readAllBytes(projectRoot().resolve(relativePath)), UTF_8);
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

	private static void assertNoSourceReferences(Path root, Path source, List<String> forbidden)
			throws IOException {
		if (!Files.isDirectory(source)) return;
		List<String> violations = new ArrayList<>();
		try (var files = Files.walk(source)) {
			List<Path> sourceFiles = files.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java") ||
							path.toString().endsWith(".kt")).toList();
			for (Path file : sourceFiles) {
				List<String> lines = Files.readAllLines(file);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i);
					for (String type : forbidden) {
						if (line.contains(type)) {
							violations.add(root.relativize(file) + ":" + (i + 1) +
									" [core UI -> concrete addon fragment] " + type);
						}
					}
				}
			}
		}
		if (!violations.isEmpty()) fail(String.join("\n", violations));
	}

	private static void assertNoForbiddenImports(Path source, String allowedPrefix)
			throws IOException {
		List<String> violations = new ArrayList<>();
		assertNoForbiddenImports(projectRoot(), source, "core", allowedPrefix, violations);
		if (!violations.isEmpty()) fail(String.join("\n", violations));
	}

	private static void assertNoForbiddenImports(Path root, Path source, String sourceModule,
			String allowedPrefix, List<String> violations) throws IOException {
		if (!Files.isDirectory(source)) return;
		try (var files = Files.walk(source)) {
			List<Path> sourceFiles = files.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java") ||
							path.toString().endsWith(".kt")).toList();
			for (Path file : sourceFiles) {
				List<String> lines = Files.readAllLines(file);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i);
					String value = line.trim();
					if ((allowedPrefix != null) && value.startsWith("package ")) {
						String declared = value.substring("package ".length()).replace(";", "").trim();
						String rootPackage = allowedPrefix.substring(0, allowedPrefix.length() - 1);
						if (!declared.equals(rootPackage) && !declared.startsWith(allowedPrefix)) {
							violations.add(root.relativize(file) + ":" + (i + 1) + " [" +
									sourceModule + " package ownership] " + value +
									"; expected " + rootPackage);
						}
					}
					if (!value.startsWith("import ")) continue;
					String imported = importedName(value);
					for (Map.Entry<String, String> target : ADDON_PACKAGES.entrySet()) {
						if (target.getValue().equals(allowedPrefix)) continue;
						if (imported.startsWith(target.getValue())) {
							violations.add(root.relativize(file) + ":" + (i + 1) + " [" +
									sourceModule + " -> " + target.getKey() + "] " + value);
						}
					}
				}
			}
		}
	}

	private static String importedName(String importLine) {
		String imported = importLine.substring("import ".length()).trim();
		if (imported.startsWith("static ")) imported = imported.substring("static ".length()).trim();
		int end = imported.indexOf(';');
		if (end >= 0) imported = imported.substring(0, end);
		end = imported.indexOf(' '); // Kotlin alias: import package.Type as Alias
		return (end < 0) ? imported : imported.substring(0, end);
	}

	private static void assertNoSiblingProjectDependencies(Path root, Path buildFile,
			String sourceModule, List<String> violations) throws IOException {
		List<String> lines = Files.readAllLines(buildFile);
		for (int i = 0; i < lines.size(); i++) {
			Matcher dependency = PROJECT_DEPENDENCY.matcher(lines.get(i));
			while (dependency.find()) {
				String targetModule = dependency.group(1);
				if (!targetModule.equals(sourceModule) && ADDON_PACKAGES.containsKey(targetModule)) {
					violations.add(root.relativize(buildFile) + ":" + (i + 1) + " [" +
							sourceModule + " -> " + targetModule + "] " + lines.get(i).trim());
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
