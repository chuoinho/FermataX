package me.aap.fermata.ui.policy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class MobileControlsAndGlobalVideoScaleContractTest {
	@Test
	public void phoneFullscreenNeverRevealsTheFloatingBackOverlay() throws Exception {
		String chrome = repositorySource(
				"modules/web/src/main/java/me/aap/fermata/addon/web/FermataChromeClient.java");
		int touch = chrome.indexOf("protected boolean onTouchEvent(View v, MotionEvent event)");
		int autoGuard = chrome.indexOf(
				"if (!a.getRuntimeHostMode().usesAutomotivePresentation())", touch);
		int phoneGone = chrome.indexOf("fb.setVisibility(GONE);", autoGuard);
		int autoVisible = chrome.indexOf("fb.setVisibility(VISIBLE);", phoneGone);
		assertTrue(touch >= 0);
		assertTrue(autoGuard > touch);
		assertTrue(phoneGone > autoGuard);
		assertTrue(autoVisible > phoneGone);
	}

	@Test
	public void videoScaleUsesOneLibraryPreferenceKeyAcrossPlayableItems() throws Exception {
		String itemBase = fermataSource("media/lib/ItemBase.java");
		String settings = fermataSource("ui/fragment/MediaEnginePrefsBuilder.java");
		String video = fermataSource("ui/view/VideoView.java");
		assertTrue(itemBase.contains(
				"if (MediaPrefs.VIDEO_SCALE.getName().equals(key.getName())) return key.getName();"));
		assertTrue(settings.contains("o.store = mediaPrefs;"));
		assertTrue(settings.contains("o.pref = MediaLibPrefs.VIDEO_SCALE;"));
		assertTrue(video.contains("item.getPrefs().getVideoScalePref()"));
		assertFalse(itemBase.contains("getId() + \"#\" + MediaPrefs.VIDEO_SCALE.getName()"));
	}

	private static String fermataSource(String relativePath) throws Exception {
		return repositorySource("fermata/src/main/java/me/aap/fermata/" + relativePath);
	}

	private static String repositorySource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
