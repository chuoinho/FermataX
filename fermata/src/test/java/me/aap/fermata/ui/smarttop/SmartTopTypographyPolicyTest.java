package me.aap.fermata.ui.smarttop;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SmartTopTypographyPolicyTest {
	@Test
	public void hierarchyKeepsTitleDominantAndSubtitleSecondary() {
		SmartTopTypographyPolicy.Typography compact =
				SmartTopTypographyPolicy.resolve(SmartTopLayoutMode.COMPACT);
		SmartTopTypographyPolicy.Typography standard =
				SmartTopTypographyPolicy.resolve(SmartTopLayoutMode.STANDARD);
		SmartTopTypographyPolicy.Typography expanded =
				SmartTopTypographyPolicy.resolve(SmartTopLayoutMode.EXPANDED);

		assertEquals(12F, compact.eyebrowSp(), 0F);
		assertEquals(20F, compact.titleSp(), 0F);
		assertEquals(13F, compact.subtitleSp(), 0F);
		assertEquals(23F, standard.titleSp(), 0F);
		assertEquals(25F, expanded.titleSp(), 0F);
		assertTrue(compact.titleSp() > compact.subtitleSp());
		assertTrue(compact.subtitleSp() > compact.eyebrowSp());
		assertTrue(expanded.titleSp() > expanded.subtitleSp());
		assertTrue(expanded.subtitleSp() > expanded.eyebrowSp());
	}

	@Test
	public void stableRowsReserveSpaceWithoutDisablingFontScaling() {
		for (SmartTopLayoutMode mode : SmartTopLayoutMode.values()) {
			SmartTopTypographyPolicy.Typography type = SmartTopTypographyPolicy.resolve(mode);
			assertTrue(type.eyebrowMinHeightDp() > 0);
			assertTrue(type.titleMinHeightDp() > type.eyebrowMinHeightDp());
			assertTrue(type.subtitleMinHeightDp() >= type.eyebrowMinHeightDp());
		}
	}

	@Test
	public void controllerLocksRowsAndPreservesFontScaleFallback() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(controller.contains("SmartTopTypographyPolicy.resolve(mode)"));
		assertTrue(controller.contains("view.setMinLines(1)"));
		assertTrue(controller.contains("view.setMaxLines(1)"));
		assertTrue(controller.contains("view.setIncludeFontPadding(false)"));
		assertTrue(controller.contains("view.setMinHeight(px(root, minHeightDp))"));
		assertTrue(controller.contains("float fontScale = root.getResources().getConfiguration().fontScale;"));
		assertTrue(controller.contains("SmartTopPresentationPolicy.resolve("));
		assertTrue(binder.contains("shouldShowSubtitle(state.eyebrow(), state.subtitle())"));
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
