package me.aap.fermata.ui.smarttop;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards the phone renderer against invisible semantic slots consuming the text budget. */
public class SmartTopMobileGeometryRegressionTest {
	@Test
	public void phoneAndAutomotiveShareAdaptiveActionGeometry() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertFalse(controller.contains("if (!automotive) return;"));
		assertTrue(controller.contains("int cell = px(root, spec.actionCellDp());"));
		assertTrue(controller.contains("int gap = px(root, spec.actionGapDp());"));
		assertTrue(controller.contains("params.width = (action == null) ? 0 : cell;"));
	}

	@Test
	public void touchBudgetRemainsFortyEightDpWithTwentyTwoDpGlyphs() {
		assertTrue(SmartTopAdaptivePolicy.TOUCH_ACTION_CELL_DP == 48);
		assertTrue(SmartTopAdaptivePolicy.TOUCH_GLYPH_DP == 22);
		assertTrue(SmartTopAdaptivePolicy.TOUCH_ACTION_GAP_DP == 4);
	}

	@Test
	public void narrowPhoneDropsAuxiliarySlotsInsteadOfCollapsingText() {
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(
				new SmartTopEnvironment(313, 720, 1F, SmartTopInteractionProfile.TOUCH),
				SmartTopActionPolicy.resolve(SmartTopMode.CURRENT,
						SmartTopCapabilities.current(true, true)),
				new SmartTopContentMetrics(120, 0, 3));
		assertTrue(spec.visibleActions().contains(SmartTopAction.PLAY_PAUSE));
		assertFalse(spec.visibleActions().contains(SmartTopAction.FAVORITE));
		assertFalse(spec.visibleActions().contains(SmartTopAction.OPEN_CONTEXT));
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
