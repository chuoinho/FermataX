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
				"dashboard_action_next", "dashboard_action_favorite",
				"dashboard_action_back_to_list"}) {
			assertTrue(id, element(layout, "@+id/" + id)
					.contains("android:visibility=\"invisible\""));
		}
	}

	@Test
	public void nonCompactActionRailIsCenteredBySpaceClassNotHostType() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		int adaptive = controller.indexOf("if (spec.centerActionRail()) {");
		int center = controller.indexOf("actionParams.verticalBias = 0.5F", adaptive);
		assertTrue(adaptive >= 0);
		assertTrue(controller.indexOf(
				"actionParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID", adaptive) < center);
		assertTrue(controller.indexOf(
				"actionParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID", adaptive) < center);
		assertTrue(center > adaptive);
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
