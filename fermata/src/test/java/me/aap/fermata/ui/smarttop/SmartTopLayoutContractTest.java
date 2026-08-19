package me.aap.fermata.ui.smarttop;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source/resource guards for the adaptive V2 renderer. */
public class SmartTopLayoutContractTest {
	@Test
	public void v2UsesDedicatedLayoutAndLegacyViewTypeRemainsAvailable() throws Exception {
		String dashboard = source("ui/fragment/DashboardFragment.java");
		assertTrue(dashboard.contains("VIEW_TYPE_SMART_TOP_V2"));
		assertTrue(dashboard.contains("R.layout.dashboard_smart_top_v2_item"));
		assertTrue(dashboard.contains("R.layout.dashboard_smart_top_item"));
		assertTrue(dashboard.contains("if (card.smartTopState != null) return VIEW_TYPE_SMART_TOP_V2"));
	}

	@Test
	public void layoutExposesAllRendererSurfacesThreeRecentRowsAndTwoLineTitle() throws Exception {
		String layout = resource("layout/dashboard_smart_top_v2_item.xml");
		for (String id : new String[]{"dashboard_action_label", "dashboard_action_prev",
				"dashboard_action_play_pause", "dashboard_action_next",
				"dashboard_action_favorite", "dashboard_action_back_to_list",
				"dashboard_smart_progress", "dashboard_smart_progress_current",
				"dashboard_smart_progress_total", "dashboard_recent_panel",
				"dashboard_recent_item_1", "dashboard_recent_item_2", "dashboard_recent_item_3"}) {
			assertTrue(id, layout.contains("@+id/" + id));
		}
		String title = element(layout, "@+id/dashboard_item_title");
		assertTrue(title.contains("android:minLines=\"2\""));
		assertTrue(title.contains("android:maxLines=\"2\""));
		for (String id : new String[]{"dashboard_recent_item_1", "dashboard_recent_item_2",
				"dashboard_recent_item_3"}) {
			String row = element(layout, "@+id/" + id);
			assertTrue(id, row.contains("android:layout_height=\"28dp\""));
			assertTrue(id, row.contains("android:ellipsize=\"end\""));
			assertTrue(id, row.contains("android:maxLines=\"1\""));
		}
	}

	@Test
	public void adaptiveControllerIsSingleWriterForGeometryAndRailNeverWraps() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertTrue(controller.contains("SmartTopAdaptivePolicy.resolve(environment, state.actions(), metrics)"));
		assertTrue(controller.contains("spec.cardHeightDp()"));
		assertTrue(controller.contains("spec.artworkSizeDp()"));
		assertTrue(controller.contains("spec.actionCellDp()"));
		assertTrue(controller.contains("spec.recentPanelWidthDp()"));
		assertTrue(controller.contains("actionParams.topToTop = R.id.dashboard_item_eyebrow"));
		assertTrue(controller.contains("actionParams.bottomToBottom = R.id.dashboard_smart_progress_group"));
		assertFalse(controller.contains("centerActionRail"));
		assertFalse(controller.contains("SmartTopPresentationPolicy"));
	}

	@Test
	public void binderConsumesAdaptiveActionsTerminalStyleAndRecentRows() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(binder.contains("SmartTopLayoutController.layoutSpec(views.root(), state)"));
		assertTrue(binder.contains("spec.visibleActions()"));
		assertTrue(binder.contains("SmartTopTerminalActionStyle.LABEL_ONLY"));
		assertTrue(binder.contains("int count = Math.min(spec.recentRows()"));
		assertFalse(binder.contains("SmartTopLayoutController.presentation("));
	}

	@Test
	public void invisibleSemanticSlotsCollapseToZeroWidth() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertTrue(controller.contains("params.width = (action == null) ? 0 : cell"));
		assertTrue(controller.contains("labelParams.width = labelActive ?"));
	}

	@Test
	public void quickRecentLoadsAndBindsUpToThreeIndependentItems() throws Exception {
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		String state = source("ui/smarttop/SmartTopViewState.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(state.contains("MAX_QUICK_RECENT = 3"));
		assertTrue(state.contains("Math.min(MAX_QUICK_RECENT, recent.size())"));
		assertTrue(coordinator.contains("SmartTopViewState.MAX_QUICK_RECENT"));
		assertTrue(binder.contains("for (int i = 0; i < count; i++)"));
	}

	@Test
	public void playPauseReusesCurrentGenerationAndTimelinePayload() throws Exception {
		String dashboard = source("ui/fragment/DashboardFragment.java");
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(dashboard.contains("PAYLOAD_SMART_TOP_TIMELINE"));
		assertTrue(dashboard.contains("notifyItemChanged(position, PAYLOAD_SMART_TOP_TIMELINE)"));
		assertTrue(dashboard.contains("bindTimelineUpdate(holder.smartTopViews(), card.smartTopState)"));
		assertTrue(coordinator.contains("refreshCurrentInPlace(active)"));
		assertTrue(coordinator.contains("listener.onSmartTopTimeline(next)"));
		assertTrue(coordinator.contains("current.quickRecent()"));
		assertTrue(binder.contains("setTag(R.id.dashboard_smart_state_tag, state)"));
	}

	@Test
	public void timelineStaysWithPrimaryContent() throws Exception {
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertTrue(controller.contains("progressParams.width = 0"));
		assertTrue(controller.contains("progressParams.topToBottom = R.id.dashboard_item_subtitle"));
		assertTrue(controller.contains("progressParams.endToStart = R.id.dashboard_item_actions"));
	}

	private static String element(String xml, String id) {
		int at = xml.indexOf(id);
		int start = xml.lastIndexOf('<', at);
		int end = xml.indexOf("/>", at);
		return xml.substring(start, end + 2);
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static String resource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res").resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
