package me.aap.fermata.ui.policy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class BackNavigationAuthorityContractTest {
	@Test
	public void mediaBackLeavesFullscreenBeforeNavigatingTheListHierarchy() throws Exception {
		String source = coreSource("ui/fragment/MediaLibFragment.java");
		int method = source.indexOf("public boolean onBackPressed()");
		int nextMethod = source.indexOf("\n\t@Override", method);
		assertTrue(method >= 0);
		assertTrue(nextMethod > method);
		String back = source.substring(method, nextMethod);
		int videoExit = back.indexOf("BackNavigationPolicy.leaveVideoMode(ad)");
		int parentNavigation = back.indexOf("BrowsableItem oldParent");
		assertTrue(videoExit >= 0);
		assertTrue(parentNavigation > videoExit);
	}

	@Test
	public void activityAndPlayerBackEnterTheSameCommonPolicy() throws Exception {
		String source = coreSource("ui/activity/MainActivityDelegate.java");
		assertTrue(source.contains("public void onPlayerBackPressed() {\n\t\tBackNavigationPolicy.handlePlayerBack(this);"));
		assertTrue(source.contains("public void onBackPressed() {\n\t\tBackNavigationPolicy.handleActivityBack(this);"));
	}

	private static String coreSource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
			current = current.getParent();
		}
		throw new AssertionError("Unable to locate repository");
	}
}
