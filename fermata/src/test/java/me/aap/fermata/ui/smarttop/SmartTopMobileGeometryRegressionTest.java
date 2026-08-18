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
	public void phoneAndAutomotiveShareMeasuredActionGeometry() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertFalse(controller.contains("if (!automotive) return;"));
		assertTrue(controller.contains("int cell = px(root, presentation.actionCellDp());"));
		assertTrue(controller.contains("int gap = px(root, presentation.actionGapDp());"));
		assertTrue(controller.contains("params.width = (action == null) ? 0 : cell;"));
	}

	@Test
	public void phoneBudgetRemainsFortyEightDpWithTwentyTwoDpGlyphs() throws Exception {
		String policy = source("ui/smarttop/SmartTopPresentationPolicy.java");
		assertTrue(policy.contains("MOBILE_ACTION_CELL_DP = 48"));
		assertTrue(policy.contains("MOBILE_GLYPH_DP = 22"));
		assertTrue(policy.contains("MOBILE_GAP_DP = 4"));
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
