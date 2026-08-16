package me.aap.fermata.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** Final architecture gate for the unified PHONE/automotive UI shell. */
public class UiShellArchitectureGuardTest {
	@Test
	public void semanticPoliciesDoNotForkByAutomotiveHost() throws IOException {
		for (String path : List.of(
				"fermata/src/main/java/me/aap/fermata/ui/policy/TopBarPolicy.java",
				"fermata/src/main/java/me/aap/fermata/ui/policy/BackNavigationPolicy.java",
				"fermata/src/main/java/me/aap/fermata/ui/activity/NavigationCoordinator.java",
				"fermata/src/main/java/me/aap/fermata/ui/policy/PlaybackPresentationReducer.java")) {
			String source = source(path);
			assertFalse(path + " must not branch on automotive presentation",
					source.contains("usesAutomotivePresentation()"));
			assertFalse(path + " must not branch on AUTO build semantics",
					source.contains("BuildConfig.AUTO"));
		}
	}

	@Test
	public void surfaceRenderersDoNotMutateOtherShellSurfaces() throws IOException {
		assertContainsNone("fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelView.java",
				"tool_bar_title", "tool_bar_back_button", "ChromePolicy.refreshTopBackButton",
				"NavBarController.refresh");
		assertContainsNone("fermata/src/main/java/me/aap/fermata/ui/view/ControlPanelPresentationView.java",
				"tool_bar_title", "tool_bar_back_button", "TopBarController", "NavBarController");
		assertContainsNone("fermata/src/main/java/me/aap/fermata/ui/view/TopBarController.java",
				"getNavBar()", "getControlPanel()", "setVideoMode(", "BodyLayout");
		assertContainsNone("fermata/src/main/java/me/aap/fermata/ui/view/NavBarController.java",
				"getToolBar()", "getControlPanel()", "setVideoMode(", "BodyLayout");
		assertContainsNone("fermata/src/main/java/me/aap/fermata/ui/fragment/NavBarMediator.java",
				".setSelected(", "BackNavigationPolicy.leaveVideoMode", "setVideoMode(", "BodyLayout");
		assertContainsNone("fermata/src/main/java/me/aap/fermata/ui/fragment/ToolBarMediator.java",
				"setVideoMode(", "BodyLayout", "getNavBar().findViewById");
	}

	@Test
	public void everyFermataToolbarGetsCanonicalBackAfterMediatorBuild() throws IOException {
		String toolbar = source("fermata/src/main/java/me/aap/fermata/ui/view/FermataToolBarView.java");
		assertTrue(toolbar.contains("findViewById(me.aap.utils.R.id.tool_bar_back_button) == null"));
		assertTrue(toolbar.contains("TopBarMediatorSupport.installBackButton(this, mediator)"));
		assertTrue(toolbar.contains("TopBarController.refresh(MainActivityDelegate.get(getContext()), fragment)"));

		String support = source("fermata/src/main/java/me/aap/fermata/ui/view/TopBarMediatorSupport.java");
		assertTrue(support.contains("public static void installBackButton("));
		assertTrue(support.contains("public static int getBackButtonSide("));
		assertTrue(support.contains("MainActivityDelegate.get(toolBar.getContext()).getNavBar().isRight()"));

		String web = source("modules/web/src/main/java/me/aap/fermata/addon/web/WebToolBarMediator.java");
		assertTrue(web.contains("TopBarMediatorSupport.installBackButton(tb, this)"));
		assertFalse(web.contains("private int getBackButtonSide("));
	}

	@Test
	public void addonCodeCannotForkCanonicalBackStructureOrVisibility() throws IOException {
		Path root = projectRoot();
		Path modules = root.resolve("modules");
		List<String> violations = new ArrayList<>();
		try (var files = Files.walk(modules)) {
			for (Path file : files.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java") ||
							path.toString().endsWith(".kt")).toList()) {
				List<String> lines = Files.readAllLines(file);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i);
					if (!line.contains("tool_bar_back_button")) continue;
					if (line.contains("setVisibility(") || line.contains("addButton(")) {
						violations.add(root.relativize(file) + ":" + (i + 1) + " " + line.trim());
					}
				}
			}
		}
		if (!violations.isEmpty()) {
			fail("Addon code must use the common top-bar Back structure/visibility authority:\n" +
					String.join("\n", violations));
		}
	}

	@Test
	public void shellInvalidationFlowsThroughSurfaceControllers() throws IOException {
		String shell = source("fermata/src/main/java/me/aap/fermata/ui/view/UiShellController.java");
		assertTrue(shell.contains("TopBarController.refresh(activity);"));
		assertTrue(shell.contains("NavBarController.refresh(activity);"));
		assertFalse(shell.contains("findViewById"));
		assertFalse(shell.contains("setVisibility("));
		assertFalse(shell.contains("setSelected("));
	}

	private static void assertContainsNone(String path, String... forbidden) throws IOException {
		String source = source(path);
		for (String value : forbidden) {
			assertFalse(path + " must not contain cross-surface writer: " + value,
					source.contains(value));
		}
	}

	private static String source(String relativePath) throws IOException {
		return Files.readString(projectRoot().resolve(relativePath));
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
