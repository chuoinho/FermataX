package me.aap.fermata.ui.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class NavBarAuthorityContractTest {
	@Test
	public void mediatorEmitsNavigationIntentsAndRendersAuthoritativeSelection() throws Exception {
		String mediator = coreSource("ui/fragment/NavBarMediator.java");
		assertTrue(mediator.contains("NavigationCoordinator.select(activity, id)"));
		assertTrue(mediator.contains("NavigationCoordinator.reselect(activity, id)"));
		assertTrue(mediator.contains("NavBarController.refresh((MainActivityDelegate) a)"));
		assertFalse(mediator.contains("BackNavigationPolicy.leaveVideoMode"));
		assertFalse(mediator.contains("private static void selectOnly"));
		assertFalse(mediator.contains("super.fragmentChanged(nb, a, f)"));
	}

	@Test
	public void navControllerIsTheSelectionWriter() throws Exception {
		String controller = coreSource("ui/view/NavBarController.java");
		assertTrue(controller.contains("applySelection(navBar, activity.getActiveNavItemId())"));
		assertTrue(controller.contains("child.setSelected"));
	}

	@Test
	public void navigationCoordinatorOwnsDestinationStateChanges() throws Exception {
		String coordinator = coreSource("ui/activity/NavigationCoordinator.java");
		assertTrue(coordinator.contains("activity.setActiveNavItemId(destinationId)"));
		assertTrue(coordinator.contains("activity.showFragmentWhenReady(destinationId)"));
		assertTrue(coordinator.contains("BackNavigationPolicy.handleNavReselection"));
	}

	private static String coreSource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
