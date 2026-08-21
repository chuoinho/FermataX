package me.aap.fermata.ui.fragment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class DashboardPlayableNavigatorContractTest {
	@Test
	public void playDecisionUsesMediaSessionItemNotNavigationSelection() throws Exception {
		String source = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardPlayableNavigator.java")), UTF_8);
		int start = source.indexOf("private static void playIfNeeded");
		int end = source.indexOf("private static void onSmartTopTargetOpened", start);
		String body = source.substring(start, end);
		assertTrue(body.contains("getMediaServiceBinder().getCurrentItem()"));
		assertFalse(body.contains("activity.getCurrentPlayable()"));
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		for (int i = 0; (i < 5) && (current != null); i++, current = current.getParent()) {
			if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		}
		throw new AssertionError("Unable to locate repository");
	}
}
