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
	public void quickRecentDataReachesAdaptiveBudgetUnfiltered() throws Exception {
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		String state = source("ui/smarttop/SmartTopViewState.java");
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");

		assertTrue(coordinator.contains("SmartTopViewState.MAX_QUICK_RECENT"));
		assertTrue(coordinator.contains("List<PlayableItem> nextRecent = recent(items, active,"));
		assertTrue(coordinator.contains("publish(current.withQuickRecent(nextRecent))"));
		assertFalse(state.contains("(layout == SmartTopLayoutMode.COMPACT) || recent.isEmpty()"));
		assertTrue(controller.contains("SmartTopAdaptivePolicy.resolve(environment, state.actions(), metrics)"));
		assertTrue(binder.contains("int count = Math.min(spec.recentRows()"));
		assertFalse(binder.contains("SmartTopLayoutController.presentation("));
	}

	@Test
	public void sameCurrentItemStillReloadsAllQuickRecentRowsWithoutNoopRebind() throws Exception {
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		int refresh = coordinator.indexOf("public void refresh()");
		int current = coordinator.indexOf("private boolean refreshCurrentInPlace", refresh);
		String refreshBody = coordinator.substring(refresh, current);
		assertTrue(refreshBody.contains("refreshCurrentInPlace(active)"));
		assertTrue(refreshBody.contains("loadQuickRecent(refreshGeneration, active)"));
		assertTrue(coordinator.contains("if (sameRecent(current.quickRecent(), nextRecent)) return;"));
		assertTrue(coordinator.contains("static boolean sameRecent("));
	}

	@Test
	public void smartTopRuntimeDoesNotRenderNextOrBackActions() throws Exception {
		String policy = source("ui/smarttop/SmartTopActionPolicy.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");
		String adaptive = source("ui/smarttop/SmartTopAdaptivePolicy.java");

		assertFalse(policy.contains("actions.add(SmartTopAction.NEXT)"));
		assertFalse(policy.contains("actions.add(SmartTopAction.OPEN_CONTEXT)"));
		assertFalse(policy.contains("actions.add(SmartTopAction.HISTORY)"));
		assertFalse(binder.contains("case NEXT -> R.drawable.next"));
		assertFalse(binder.contains("case OPEN_CONTEXT -> R.drawable.view_list"));
		assertFalse(adaptive.contains("case NEXT -> 2"));
		assertFalse(adaptive.contains("case OPEN_CONTEXT, HISTORY -> 3"));
	}

	@Test
	public void persistentQuickRecentIsNotDroppedByCompactOrWidthPressure() throws Exception {
		String adaptive = source("ui/smarttop/SmartTopAdaptivePolicy.java");
		assertTrue(adaptive.contains("case COMPACT -> 148"));
		assertFalse(adaptive.contains("recentRows = 0;"));
		assertFalse(adaptive.contains("recentPanelWidthDp = 0;"));
	}

	@Test
	public void legacyPresentationPolicyIsNotReferencedByRuntime() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertFalse(controller.contains("SmartTopPresentationPolicy"));
		assertFalse(binder.contains("SmartTopPresentationPolicy"));
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
