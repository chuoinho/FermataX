package me.aap.fermata.addon.tv;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class TvUiShellIntegrationContractTest {
	@Test
	public void tvInheritsCommonToolbarAndBackSemantics() throws Exception {
		String tv = source("modules/tv/src/main/java/me/aap/fermata/addon/tv/TvFragment.java");
		String media = source("fermata/src/main/java/me/aap/fermata/ui/fragment/MediaLibFragment.java");

		assertTrue(tv.contains("public class TvFragment extends MediaLibFragment"));
		assertTrue(tv.contains("return me.aap.fermata.R.id.tv_fragment;"));
		assertFalse(tv.contains("getToolBarMediator()"));
		assertFalse(tv.contains("public boolean onBackPressed()"));

		assertTrue(media.contains("return ToolBarMediator.instance;"));
		assertTrue(media.contains("if (BackNavigationPolicy.leaveVideoMode(ad)) return true;"));
	}

	@Test
	public void tvAddonCannotForkChromeOrVideoLayoutAuthority() throws Exception {
		String tv = source("modules/tv/src/main/java/me/aap/fermata/addon/tv/TvFragment.java");

		assertTrue(tv.contains("public void navBarItemReselected(int itemId)"));
		assertTrue(tv.contains("getAdapter().setParent(getRootItem())"));
		assertFalse(tv.contains("setVideoMode("));
		assertFalse(tv.contains("BodyLayout"));
		assertFalse(tv.contains("TopBarController"));
		assertFalse(tv.contains("BackNavigationPolicy"));
	}

	private static String source(String relativePath) throws Exception {
		return Files.readString(projectRoot().resolve(relativePath), UTF_8);
	}

	private static Path projectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("settings.gradle")) &&
					Files.isDirectory(current.resolve("modules/tv"))) return current;
			current = current.getParent();
		}
		throw new AssertionError("Unable to locate FermataX project root");
	}
}
