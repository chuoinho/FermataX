package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Source contracts for Dashboard regressions reproduced on the universal APK phone host. */
public class DashboardRuntimeRegressionContractTest {
	@Test
	public void localizedContextNeverOwnsRuntimeHostResolution() throws Exception {
		String builder = source(
				"fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardModelBuilder.java");
		assertFalse(builder.contains("MainActivityDelegate.get(ctx)"));
		assertTrue(builder.contains("private final boolean automotive;"));
		assertTrue(builder.contains(
				"DashboardModelBuilder(Context ctx, PreferenceStore store, boolean automotive)"));

		String dashboard = source(
				"fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java");
		assertTrue(dashboard.contains("new DashboardModelBuilder(ctx, store,"));
		assertTrue(dashboard.contains(
				"activity.getRuntimeHostMode().usesAutomotivePresentation())"));
	}

	@Test
	public void smartTopWiresAllThreeQuickRecentRowsIntoBinder() throws Exception {
		String dashboard = source(
				"fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java");
		assertTrue(dashboard.contains(
				"List.of(recentItems[0], recentItems[1], recentItems[2])"));
		assertFalse(dashboard.contains("recentPanel, recentTitle, List.of(recentItems[0]));"));
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(relativePath)),
				StandardCharsets.UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("settings.gradle")) &&
					Files.isDirectory(current.resolve("fermata"))) return current;
			current = current.getParent();
		}
		throw new AssertionError("Unable to locate repository root");
	}
}
