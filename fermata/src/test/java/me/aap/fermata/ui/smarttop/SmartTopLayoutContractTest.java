package me.aap.fermata.ui.smarttop;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source/resource guards for the isolated V2 renderer and its visual geometry. */
public class SmartTopLayoutContractTest {
	@Test
	public void v2UsesADedicatedLayoutAndLegacyViewTypeRemainsAvailable() throws Exception {
		String dashboard = source("ui/fragment/DashboardFragment.java");
		assertTrue(dashboard.contains("VIEW_TYPE_SMART_TOP_V2"));
		assertTrue(dashboard.contains("R.layout.dashboard_smart_top_v2_item"));
		assertTrue(dashboard.contains("R.layout.dashboard_smart_top_item"));
		assertTrue(dashboard.contains("if (card.smartTopState != null) return VIEW_TYPE_SMART_TOP_V2"));
	}

	@Test
	public void controlsSeparateVisualSurfaceGlyphAndTouchGeometry() throws Exception {
		String layout = resource("layout/dashboard_smart_top_v2_item.xml");
		String dimensions = resource("values/dimens.xml");
		assertTrue(dimensions.contains("dashboard_smart_v2_control_size\">48dp"));
		assertTrue(dimensions.contains("dashboard_smart_v2_visual_action_size\">44dp"));
		assertTrue(dimensions.contains("dashboard_smart_v2_action_glyph_size\">22dp"));
		assertTrue(layout.contains("android:layout_width=\"@dimen/dashboard_smart_v2_control_size\""));
		assertTrue(layout.contains("android:layout_height=\"@dimen/dashboard_smart_v2_control_size\""));
		assertTrue(layout.contains("android:padding=\"@dimen/dashboard_smart_v2_action_padding\""));
		assertTrue(layout.contains("@drawable/dashboard_smart_action_v2_bg"));
	}

	@Test
	public void layoutExposesAllSixStateRendererSurfacesWithoutExtraRecentRows() throws Exception {
		String layout = resource("layout/dashboard_smart_top_v2_item.xml");
		for (String id : new String[]{"dashboard_action_label", "dashboard_action_prev",
				"dashboard_action_play_pause", "dashboard_action_next",
				"dashboard_action_favorite", "dashboard_action_back_to_list",
				"dashboard_smart_progress", "dashboard_smart_progress_current",
				"dashboard_smart_progress_total", "dashboard_recent_panel",
				"dashboard_recent_item_1"}) {
			assertTrue(id, layout.contains("@+id/" + id));
		}
		assertTrue(layout.contains("@+id/dashboard_recent_item_2"));
		assertTrue(layout.contains("@+id/dashboard_recent_item_3"));
		assertTrue(layout.contains("android:layout_height=\"0dp\""));
		assertFalse(layout.contains("android:layout_height=\"28dp\"\n                android:visibility=\"gone\""));
	}

	@Test
	public void rtlLongTextAndFocusUseBoundedLogicalGeometry() throws Exception {
		String layout = resource("layout/dashboard_smart_top_v2_item.xml");
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertTrue(layout.contains("android:ellipsize=\"end\""));
		assertTrue(layout.contains("android:maxLines=\"1\""));
		assertTrue(layout.contains("android:maxWidth=\"112dp\""));
		assertTrue(layout.contains("android:layout_marginStart"));
		assertTrue(layout.contains("android:layout_marginEnd"));
		assertFalse(layout.contains("android:layout_marginLeft"));
		assertFalse(layout.contains("android:layout_marginRight"));
		assertTrue(controller.contains("setMarginStart"));
		assertTrue(controller.contains("setMarginEnd"));
		assertTrue(controller.indexOf("dashboard_action_label") <
				controller.indexOf("dashboard_action_prev"));
		assertTrue(layout.indexOf("dashboard_action_prev") <
				layout.indexOf("dashboard_action_play_pause"));
		assertTrue(layout.indexOf("dashboard_action_play_pause") <
				layout.indexOf("dashboard_action_next"));
		assertTrue(layout.indexOf("dashboard_action_next") <
				layout.indexOf("dashboard_action_favorite"));
		assertTrue(layout.indexOf("dashboard_action_favorite") <
				layout.indexOf("dashboard_action_back_to_list"));
	}

	@Test
	public void binderClearsRecycledActionsAndGuardsLateRecentMetadata() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(binder.contains("clearLabeledAction"));
		assertTrue(binder.contains("for (ImageButton button : buttons) clearAction(button)"));
		assertTrue(binder.contains("setOnClickListener(null)"));
		assertTrue(binder.contains("setContentDescription(null)"));
		assertTrue(binder.contains("setActivated(false)"));
		assertTrue(binder.contains("token.equals(view.getTag(R.id.dashboard_smart_bind_token))"));
	}

	@Test
	public void timelinePayloadAvoidsFullBindAndRefreshesTheCurrentStateIdentity() throws Exception {
		String dashboard = source("ui/fragment/DashboardFragment.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(dashboard.contains("PAYLOAD_SMART_TOP_TIMELINE"));
		assertTrue(dashboard.contains("notifyItemChanged(position, PAYLOAD_SMART_TOP_TIMELINE)"));
		assertTrue(dashboard.contains("bindTimelineUpdate(holder.smartTopViews(), card.smartTopState)"));
		assertTrue(binder.contains("setTag(R.id.dashboard_smart_state_tag, state)"));
		assertTrue(binder.contains("SmartTopViewState current = boundState(views.root())"));
		assertTrue(binder.contains("dispatchAction(button, root)"));
	}

	@Test
	public void hiddenTimelineKeepsGeometryAndLayoutApplicationIsTokenGuarded() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertTrue(binder.contains("progressGroup().setVisibility(View.INVISIBLE)"));
		assertTrue(binder.contains("progress().setVisibility(View.INVISIBLE)"));
		assertTrue(binder.contains("progressTotal().setVisibility(View.INVISIBLE)"));
		assertTrue(binder.contains("actions().setVisibility(actions.isEmpty() ? View.INVISIBLE"));
		assertTrue(controller.contains("dashboard_smart_layout_token"));
		assertTrue(controller.contains("if (token.equals(root.getTag("));
		assertTrue(controller.contains("boolean showContext"));
	}

	@Test
	public void timelineStaysWithPrimaryContentAndRecentHeaderMatchesTheEyebrow() throws Exception {
		String layout = resource("layout/dashboard_smart_top_v2_item.xml");
		String controller = source("ui/smarttop/SmartTopLayoutController.java");
		assertTrue(controller.contains("progressParams.width = 0"));
		assertTrue(controller.contains("progressParams.topToBottom = R.id.dashboard_item_subtitle"));
		assertTrue(controller.contains("progressParams.endToStart = R.id.dashboard_item_actions"));
		assertTrue(layout.contains("android:layout_height=\"5dp\""));
		assertTrue(layout.contains("android:progressDrawable=\"@drawable/dashboard_smart_progress\""));
		String recent = element(layout, "@+id/dashboard_recent_title");
		assertTrue(recent.contains("android:textAllCaps=\"true\""));
		assertTrue(recent.contains("android:textColor=\"?attr/colorOnSecondary\""));
		assertTrue(recent.contains("android:textSize=\"13sp\""));
		assertTrue(recent.contains("android:textStyle=\"bold\""));
	}

	@Test
	public void actionRailUsesStableSemanticSlotsAndEmptyRecentIsStructural() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(binder.contains("case PLAY, PLAY_PAUSE, OPEN_ADDONS, RETRY -> buttons.get(1)"));
		assertTrue(binder.contains("case OPEN_CONTEXT, HISTORY -> buttons.get(3)"));
		assertTrue(binder.contains("case FAVORITE -> buttons.get(4)"));
		assertTrue(binder.contains("button.setVisibility(View.INVISIBLE)"));
		assertTrue(binder.contains("&&\n\t\t\t\thasContent"));
		assertTrue(binder.contains("recentPanel().setVisibility(showPanel ? View.VISIBLE : View.GONE)"));
	}

	@Test
	public void coldLaunchStartsRecentAlongsideProviderDiscovery() throws Exception {
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		int method = coordinator.indexOf("private void loadProviderCandidates");
		int preview = coordinator.indexOf("loadRecentPreview(generation);", method);
		int providers = coordinator.indexOf("providers.loadCandidates()", method);
		assertTrue(preview > method);
		assertTrue(preview < providers);
		assertTrue(coordinator.contains("getRecent().getUnsortedChildren()"));
		assertTrue(coordinator.contains("return empty(refreshGeneration, layout)"));
		assertTrue(coordinator.contains("current.mode() != SmartTopMode.EMPTY"));
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
