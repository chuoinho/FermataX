package me.aap.fermata.ui.policy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Integration contracts for the device-tested PHONE controls and global video-scale behavior. */
public class MobileControlsAndGlobalVideoScaleContractTest {
	@Test
	public void phoneBackOverlayIsSuppressedByCommonFloatingButtonBoundary() throws Exception {
		String button = fermataSource("ui/view/FermataFloatingButton.java");
		assertTrue(button.contains("!activity.getRuntimeHostMode().usesAutomotivePresentation()"));
		assertTrue(button.contains("activity.isVideoMode() || !activity.isRootPage()"));
		assertTrue(button.contains("super.setVisibility((visibility == VISIBLE)"));

		String mediator = fermataSource("ui/fragment/FloatingButtonMediator.java");
		assertTrue(mediator.contains("updateVisibility(fb, MainActivityDelegate.get(fb.getContext()), f)"));
		assertTrue(mediator.contains("if (BuildConfig.AUTO || a.isVideoMode()) return true;"));
		assertTrue(mediator.contains("id == R.id.youtube_fragment"));
		assertTrue(mediator.contains("id == R.id.web_browser_fragment"));
	}

	@Test
	public void phonePlayerbarEdgeIsPassiveWhileAutomotiveKeepsItsBackTarget() throws Exception {
		for (String layout : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String xml = repositorySource("fermata/src/main/res/layout/" + layout);
			assertTrue(xml.contains("me.aap.fermata.ui.view.ControlPanelLeadingActionView"));
		}
		String leading = fermataSource("ui/view/ControlPanelLeadingActionView.java");
		assertTrue(leading.contains("if (usesAutomotivePresentation()) return;"));
		assertTrue(leading.contains("icon.setVisibility(GONE)"));
		assertTrue(leading.contains("setOnClickListener(null)"));
		assertTrue(leading.contains("setOnTouchListener(null)"));
		assertTrue(leading.contains("time.setClickable(false)"));
	}

	@Test
	public void transportButtonsUseMeasuredCellGeometryInsteadOfFixedVisualSizing() throws Exception {
		for (String layout : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String xml = repositorySource("fermata/src/main/res/layout/" + layout);
			int count = xml.split("me\\.aap\\.fermata\\.ui\\.view\\.AdaptiveTransportButton", -1).length - 1;
			assertTrue(layout, count == 5);
			String favorite = element(xml, "@+id/control_favorite");
			assertTrue(favorite.contains("android:layout_width=\"@dimen/control_panel_edge_action_size\""));
			assertTrue(favorite.contains("android:padding=\"@dimen/control_panel_edge_action_padding\""));
		}
		String button = fermataSource("ui/view/AdaptiveTransportButton.java");
		assertTrue(button.contains("ControlPanelSizingPolicy.resolve(width, height)"));
		assertTrue(button.contains("new InsetDrawable(background, insetX, insetY, insetX, insetY)"));
		assertFalse(button.contains("16dp"));
	}

	@Test
	public void videoScaleUsesOneLibraryPreferenceAcrossItemsAndWebVideoReplacement() throws Exception {
		String itemBase = fermataSource("media/lib/ItemBase.java");
		String video = fermataSource("ui/view/VideoView.java");
		assertTrue(itemBase.contains(
				"if (MediaPrefs.VIDEO_SCALE.getName().equals(key.getName())) return key.getName();"));
		assertTrue(video.contains("item.getPrefs().getVideoScalePref()"));
		assertFalse(itemBase.contains("getId() + \"#\" + MediaPrefs.VIDEO_SCALE.getName()"));

		String chrome = repositorySource(
				"modules/web/src/main/java/me/aap/fermata/addon/web/FermataChromeClient.java");
		String bridge = repositorySource(
				"modules/web/src/main/java/me/aap/fermata/addon/web/WebVideoScaleController.java");
		assertTrue(chrome.contains("videoScale.attach()"));
		assertTrue(chrome.contains("videoScale.detach()"));
		assertTrue(bridge.contains("mediaPrefs.getIntPref(VIDEO_SCALE)"));
		assertTrue(bridge.contains("new MutationObserver"));
		assertTrue(bridge.contains("setProperty(p, value, 'important')"));
	}

	private static String element(String xml, String id) {
		int at = xml.indexOf(id);
		int start = xml.lastIndexOf('<', at);
		int end = xml.indexOf("/>", at);
		return xml.substring(start, end + 2);
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
