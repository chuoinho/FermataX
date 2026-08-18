package me.aap.fermata.ui.fragment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards the first-run transition against duplicate recreation ownership. */
public class InitialSetupTransitionRegressionTest {
	@Test
	public void setupWritesPresentationPrefsOnlyWhenTheyActuallyChanged() throws Exception {
		String setup = source("ui/fragment/InitialSetupFragment.java");
		assertTrue(setup.contains("boolean navChanged = prefs.getNavBarPosPref(activity) != nav;"));
		assertTrue(setup.contains("boolean localeChanged = !prefs.getLocalePref().equals("));
		assertTrue(setup.contains("if (navChanged) {"));
		assertTrue(setup.contains("if (localeChanged) edit.setIntPref(LOCALE, locale);"));
	}

	@Test
	public void activityPreferenceListenerOwnsRecreationAndSetupDoesNotScheduleAnotherOne()
			throws Exception {
		String setup = source("ui/fragment/InitialSetupFragment.java");
		assertFalse(setup.contains("if (recreate) activity.recreate();"));
		assertTrue(setup.contains("if (!presentationChanged) activity.showDashboard();"));

		String activity = source("ui/activity/MainActivityDelegate.java");
		assertTrue(activity.contains("MainActivityPrefs.hasNavBarPosPref(this, prefs)"));
		assertTrue(activity.contains("prefs.contains(LOCALE)"));
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
