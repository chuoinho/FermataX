package me.aap.fermata.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Source contracts for device regressions found during the final UI-shell acceptance pass. */
public class UiRuntimeRegressionContractTest {
	@Test
	public void phoneFloatingButtonCannotBecomeBackOverlay() throws Exception {
		for (String layout : new String[]{"main_activity_left.xml", "main_activity_right.xml"}) {
			String xml = resource("layout/" + layout);
			assertTrue(xml.contains("me.aap.fermata.ui.view.FermataFloatingButton"));
			assertFalse(xml.contains("<me.aap.utils.ui.view.FloatingButton"));
		}

		String button = source("fermata/src/main/java/me/aap/fermata/ui/view/FermataFloatingButton.java");
		assertTrue(button.contains("!activity.getRuntimeHostMode().usesAutomotivePresentation()"));
		assertTrue(button.contains("activity.isVideoMode() || !activity.isRootPage()"));
		assertTrue(button.contains("super.setVisibility((visibility == VISIBLE)"));
	}

	@Test
	public void phonePlayerbarLeadingRegionIsPassiveButAutomotiveKeepsBack() throws Exception {
		for (String layout : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String xml = resource("layout/" + layout);
			assertTrue(xml.contains("<me.aap.fermata.ui.view.ControlPanelLeadingActionView"));
		}

		String leading = source(
				"fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelLeadingActionView.java");
		assertTrue(leading.contains("if (usesAutomotivePresentation()) return;"));
		assertTrue(leading.contains("icon.setVisibility(GONE)"));
		assertTrue(leading.contains("setOnClickListener(null)"));
		assertTrue(leading.contains("setOnTouchListener(null)"));
		assertTrue(leading.contains("time.setClickable(false)"));
	}

	@Test
	public void nativeVideoScaleUsesOneLibraryKeyAcrossItems() throws Exception {
		String itemBase = source("fermata/src/main/java/me/aap/fermata/media/lib/ItemBase.java");
		assertTrue(itemBase.contains(
				"if (MediaPrefs.VIDEO_SCALE.getName().equals(key.getName())) return key.getName();"));
		assertTrue(itemBase.contains("return getId() + \"#\" + key.getName();"));

		String menu = source("fermata/src/main/java/me/aap/fermata/ui/view/MediaItemMenuHandler.java");
		assertTrue(menu.contains("item.getPrefs().hasPref(VIDEO_SCALE, false)"));
		assertTrue(menu.contains("item.getPrefs().setVideoScalePref"));
		assertTrue(menu.contains("item.getPrefs().removePref(VIDEO_SCALE)"));

		String video = source("fermata/src/main/java/me/aap/fermata/ui/view/VideoView.java");
		assertTrue(video.contains("item.getPrefs().getVideoScalePref()"));
	}

	@Test
	public void browserVideoScaleSurvivesCssAndSpaVideoReplacement() throws Exception {
		String chrome = source(
				"modules/web/src/main/java/me/aap/fermata/addon/web/FermataChromeClient.java");
		assertTrue(chrome.contains("videoScale.attach()"));
		assertTrue(chrome.contains("videoScale.detach()"));

		String bridge = source(
				"modules/web/src/main/java/me/aap/fermata/addon/web/WebVideoScaleController.java");
		assertTrue(bridge.contains("mediaPrefs.getIntPref(VIDEO_SCALE)"));
		assertTrue(bridge.contains("mediaPrefs.applyIntPref(false, VIDEO_SCALE"));
		assertTrue(bridge.contains("new MutationObserver"));
		assertTrue(bridge.contains("setProperty(p, value, 'important')"));
		assertTrue(bridge.contains("case SCALE_4_3 -> \"4:3\""));
		assertTrue(bridge.contains("case SCALE_16_9 -> \"16:9\""));

		String youtube = source(
				"modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeWebView.java");
		assertTrue(youtube.contains("object-fit: contain !important"));
		assertTrue(youtube.contains("v.style.objectFit"));
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(relativePath)),
				StandardCharsets.UTF_8);
	}

	private static String resource(String relativePath) throws Exception {
		return source("fermata/src/main/res/" + relativePath);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("settings.gradle")) &&
					Files.isDirectory(current.resolve("fermata"))) return current;
			current = current.getParent();
		}
		throw new AssertionError("Unable to locate repository root");
	}
}
