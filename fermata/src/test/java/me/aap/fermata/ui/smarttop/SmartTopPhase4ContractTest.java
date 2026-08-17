package me.aap.fermata.ui.smarttop;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SmartTopPhase4ContractTest {
	@Test
	public void recommendationRemainsCompatibleButUnreachableFromDisplayFlow() throws Exception {
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		String selection = source("ui/smarttop/SmartTopSelectionPolicy.java");
		String mode = source("ui/smarttop/SmartTopMode.java");
		String candidate = source("addon/SmartTopCandidate.java");

		assertFalse(coordinator.contains("SmartTopCandidate.Kind.RECOMMENDED"));
		assertFalse(coordinator.contains("SmartTopMode.RECOMMENDED"));
		assertFalse(selection.contains("return SmartTopMode.RECOMMENDED"));
		assertTrue(mode.contains("RECOMMENDED"));
		assertTrue(candidate.contains("RECOMMENDED"));
	}

	@Test
	public void quickRecentDataReachesMeasuredPresentationBudgetUnfiltered() throws Exception {
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");

		assertFalse(coordinator.contains("SmartTopLayoutPolicy.showQuickRecent("));
		assertFalse(controller.contains("SmartTopLayoutPolicy.showQuickRecent("));
		assertTrue(coordinator.contains("publish(current.withQuickRecent(recent(items, active, 1)))"));
		assertTrue(binder.contains(
				"SmartTopLayoutController.presentation(views.root(), state).showQuickRecent()"));
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
