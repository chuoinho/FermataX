package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class NavigationCoordinatorTest {
	@Test
	public void topLevelRouteBecomesTheSelectedDestination() {
		assertEquals(42, NavigationCoordinator.resolveRouteSelection(7, 42, true));
	}

	@Test
	public void nonNavRoutePreservesThePreviousTopLevelDestination() {
		int tvDestination = 42;
		int settingsRoute = 99;
		assertEquals(tvDestination,
				NavigationCoordinator.resolveRouteSelection(tvDestination, settingsRoute, false));
	}

	@Test
	public void controlSelectionUsesTheDedicatedControlRoute() throws Exception {
		String source = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata/ui/activity/NavigationCoordinator.java")));
		assertTrue(source.contains("if (destinationId == R.id.control_fragment)"));
		assertTrue(source.contains("activity.showControl();"));
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
