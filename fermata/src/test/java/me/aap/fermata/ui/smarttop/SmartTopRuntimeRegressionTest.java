package me.aap.fermata.ui.smarttop;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for issues found during final DHU and phone validation. */
public class SmartTopRuntimeRegressionTest {
	@Test
	public void duplicateEyebrowSubtitleIsSuppressed() {
		assertFalse(SmartTopBinder.shouldShowSubtitle("RECENT", "Recent"));
		assertFalse(SmartTopBinder.shouldShowSubtitle(" recent ", "RECENT"));
		assertFalse(SmartTopBinder.shouldShowSubtitle("RECENT", ""));
		assertTrue(SmartTopBinder.shouldShowSubtitle("RECENT", "YouTube"));
	}

	@Test
	public void coldEmptyCardCannotFlashDefaultIconActionsBeforeBind() throws Exception {
		String layout = resource("layout/dashboard_smart_top_v2_item.xml");
		assertTrue(element(layout, "@+id/dashboard_item_actions")
				.contains("android:visibility=\"invisible\""));
		for (String id : new String[]{"dashboard_action_prev", "dashboard_action_play_pause",
				"dashboard_action_favorite"}) {
			assertTrue(id, element(layout, "@+id/" + id)
					.contains("android:visibility=\"invisible\""));
		}
	}

	@Test
	public void retiredNextAndBackControlsAreAbsentFromEverySmartTopLayout() throws Exception {
		for (String path : new String[]{
				"layout/dashboard_smart_top_v2_item.xml",
				"layout/dashboard_smart_top_item.xml",
				"layout-w460dp/dashboard_smart_top_item.xml",
				"layout-w558dp/dashboard_smart_top_item.xml"}) {
			String layout = resource(path);
			assertFalse(path, layout.contains("dashboard_action_next"));
			assertFalse(path, layout.contains("dashboard_action_back_to_list"));
		}
	}

	@Test
	public void actionRailIsAlwaysAlignedToTheStableMetadataBlock() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertTrue(controller.contains(
				"actionParams.topToTop = R.id.dashboard_item_eyebrow"));
		assertTrue(controller.contains(
				"actionParams.bottomToBottom = R.id.dashboard_smart_progress_group"));
		assertTrue(controller.contains("actionParams.verticalBias = 0.5F"));
		assertFalse(controller.contains("centerActionRail"));
		assertFalse(controller.contains(
				"actionParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID"));
	}

	@Test
	public void terminalCtaCanDropIconWithoutDroppingItsLabel() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(binder.contains("SmartTopTerminalActionStyle.LABEL_ONLY"));
		assertTrue(binder.contains("button.setIcon(null)"));
		assertTrue(binder.contains("button.setText(description(action, state))"));
	}

	private static String element(String xml, String id) {
		int at = xml.indexOf(id);
		if (at < 0) throw new AssertionError("Missing " + id);
		int start = xml.lastIndexOf('<', at);
		int end = xml.indexOf('>', at);
		return xml.substring(start, end + 1);
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static String resource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res").resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
