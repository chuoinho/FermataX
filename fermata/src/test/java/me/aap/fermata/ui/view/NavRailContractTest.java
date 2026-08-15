package me.aap.fermata.ui.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source/resource contracts for the shared rail's architectural boundaries. */
public class NavRailContractTest {
	@Test
	public void everyHostUsesTheSharedRailAndVoiceRemainsAnAction() throws Exception {
		String mediator = source("ui/fragment/NavBarMediator.java");
		String addVoice = method(mediator, "private void addVoiceButton",
				"private static void onVoiceClick");
		String voiceClick = method(mediator, "private static void onVoiceClick",
				"public void disable");

		assertFalse(addVoice.contains("getRuntimeHostMode().isProjection()"));
		assertTrue(addVoice.contains("R.id.nav_voice"));
		assertTrue(voiceClick.contains("startGlobalVoiceControl()"));
		assertFalse(voiceClick.contains("setActiveNavItemId"));
		assertFalse(voiceClick.contains("showFragment"));
	}

	@Test
	public void homeAndVoiceAreFixedWhileDestinationsUseTheViewport() throws Exception {
		String rail = source("ui/view/FermataNavBarView.java");
		String routing = method(rail, "private void addNavigationItem",
				"private void onNavigationItemFocusChanged");

		assertTrue(routing.contains("R.id.dashboard_fragment"));
		assertTrue(routing.contains("R.id.nav_voice"));
		assertTrue(routing.contains("fixedZone.addView(child)"));
		assertTrue(routing.contains("scrollViewport.addNavigationItem(child)"));
	}

	@Test
	public void passiveScrollAffordancesCannotReorderOrPersistDashboardItems() throws Exception {
		String viewport = source("ui/view/NavRailViewport.java");
		String dashboardItems = source("ui/fragment/DashboardItems.java");
		String mediator = source("ui/fragment/NavBarMediator.java");

		assertFalse(viewport.contains("DashboardItems"));
		assertFalse(viewport.contains(".move("));
		assertFalse(viewport.contains(".swap("));
		assertFalse(viewport.contains("AppCompatImageButton"));
		assertFalse(viewport.contains("nav_scroll_up"));
		assertFalse(viewport.contains("nav_scroll_down"));
		assertTrue(viewport.contains("topFade"));
		assertTrue(viewport.contains("bottomFade"));
		assertTrue(viewport.contains("scrollThumb"));
		assertFalse(dashboardItems.contains("nav_scroll_up"));
		assertFalse(dashboardItems.contains("nav_scroll_down"));
		assertFalse(dashboardItems.contains("nav_voice"));
		assertTrue(mediator.contains("protected boolean canSwap(NavBarView nb)"));
		assertTrue(method(mediator, "protected boolean canSwap",
				"protected CharSequence getText").contains("return false"));
	}

	@Test
	public void reloadRestoresVoiceAndLogicalClearKeepsRailStructure() throws Exception {
		String mediator = source("ui/fragment/NavBarMediator.java");
		String reload = method(mediator, "public void reload",
				"public void onActivityEvent");
		String rail = source("ui/view/FermataNavBarView.java");
		String clear = method(rail, "public void removeAllViews",
				"public void setSize");

		assertTrue(reload.contains("super.reload(nb)"));
		assertTrue(reload.contains("addVoiceButton(nb)"));
		assertTrue(clear.contains("fixedZone.removeAllViews()"));
		assertTrue(clear.contains("scrollViewport.clearNavigationItems()"));
		assertFalse(clear.contains("fixedDivider = null"));
	}

	@Test
	public void clippedViewportMeasuresContentWithoutClampingItsHeight() throws Exception {
		String viewport = source("ui/view/NavRailViewport.java");
		assertTrue(viewport.contains("clip = new UnboundedHeightClip(context)"));
		assertTrue(viewport.contains("MeasureSpec.UNSPECIFIED"));
		assertTrue(viewport.contains("content.getHeight() > clip.getHeight()"));
	}

	@Test
	public void selectorsDistinguishFocusSelectionAndPress() throws Exception {
		for (String name : new String[]{"aa_projected_nav_button_bg_left.xml",
				"aa_projected_nav_button_bg_right.xml"}) {
			String selector = resource("drawable/" + name);
			assertTrue(selector.contains("aa_nav_button_selected_focused"));
			assertTrue(selector.contains("state_selected=\"true\""));
			assertTrue(selector.contains("state_focused=\"true\""));
			assertTrue(selector.contains("state_pressed=\"true\""));
			assertTrue(selector.contains("state_enabled=\"false\""));
		}
	}

	@Test
	public void sharedIconsUseAnExplicitUndistortedExtent() throws Exception {
		String rail = source("ui/view/FermataNavBarView.java");
		String icon = method(rail, "private void configureIcon",
				"private MainActivityDelegate getMainActivity");

		assertTrue(icon.contains("NavRailLayoutPolicy.iconExtentDp()"));
		assertTrue(icon.contains(".width = iconExtent"));
		assertTrue(icon.contains(".height = iconExtent"));
		assertTrue(icon.contains(".weight = 0F"));
		assertTrue(icon.contains("ScaleType.FIT_CENTER"));
		assertTrue(icon.contains("button.setIconPadding(0)"));
	}

	@Test
	public void legacyScrollBranchIsRemoved() throws Exception {
		String rail = source("ui/view/FermataNavBarView.java");

		assertFalse(rail.contains("isEnhancedRail"));
		assertFalse(rail.contains("ObjectAnimator"));
		assertFalse(rail.contains("dispatchDraw"));
		assertFalse(rail.contains("NAV_BAR_SCROLL_NUDGE_AA"));
		assertFalse(rail.contains("getScrollY()"));
		assertFalse(rail.contains("scrollTo("));
	}

	@Test
	public void mediatorReloadCannotRestoreTheLegacyMobileWidth() throws Exception {
		String rail = source("ui/view/FermataNavBarView.java");
		String mediator = method(rail, "protected boolean setMediator",
				"public void forEachNavigationItem");

		assertTrue(mediator.contains("super.setMediator(fragment)"));
		assertTrue(mediator.contains("if (initialized) setSize("));
	}

	@Test
	public void railLayoutsAlwaysDeclareAValidVerticalPosition() throws Exception {
		String left = resource("layout/main_activity_left.xml");
		String right = resource("layout/main_activity_right.xml");

		assertTrue(left.contains("app:position=\"LEFT\""));
		assertTrue(right.contains("app:position=\"RIGHT\""));
	}

	@Test
	public void customGestureThresholdOwnsMoveRoutingAndLocksTheChosenAxis() throws Exception {
		String rail = source("ui/view/FermataNavBarView.java");
		String dispatch = method(rail, "public boolean dispatchTouchEvent",
				"protected boolean interceptTouchEvent");
		String intercept = method(rail, "protected boolean interceptTouchEvent",
				"private NavRailLayoutPolicy.GestureAxis resolveGestureAxis");
		String scroll = method(rail, "public boolean onScroll",
				"protected void onDetachedFromWindow");

		assertTrue(dispatch.contains("gestureAxis = resolveGestureAxis(event)"));
		assertTrue(dispatch.contains("dispatchCancelToPressedChild(event)"));
		assertFalse(intercept.contains("gestureDetector.onTouchEvent(event)"));
		assertTrue(scroll.contains("GestureAxis.VERTICAL"));
		assertTrue(scroll.contains("GestureAxis.HORIZONTAL"));
		assertTrue(scroll.contains("distanceX, 0F"));
		assertTrue(method(rail, "public boolean onSwipeLeft",
				"public boolean onSwipeRight").contains("GestureAxis.HORIZONTAL"));
		assertTrue(method(rail, "public boolean onSwipeRight",
				"public boolean onScroll").contains("GestureAxis.HORIZONTAL"));
	}

	@Test
	public void addonContentUpdatesCannotSnapTheRailBackToItsActiveIcon() throws Exception {
		String rail = source("ui/view/FermataNavBarView.java");
		String activityEvent = method(rail, "public void onActivityEvent",
				"public boolean onSwipeLeft");
		String layout = method(rail, "protected void onLayout",
				"public void onActivityEvent");
		String voiceVisibility = method(rail, "public void setVoiceVisible",
				"private void ensureRailStructure");
		String refresh = method(rail, "private void refreshScrollState",
				"private void ensureActiveItemVisible");

		assertTrue(activityEvent.contains("event == FRAGMENT_CHANGED"));
		assertTrue(activityEvent.contains("post(refreshScrollStateTask)"));
		assertTrue(activityEvent.contains("event == FRAGMENT_CONTENT_CHANGED"));
		assertTrue(activityEvent.contains("post(refreshContentScrollStateTask)"));
		assertTrue(layout.contains("refreshScrollState(false)"));
		assertFalse(layout.contains("refreshScrollState(true)"));
		assertTrue(voiceVisibility.contains("voice.getVisibility() == visibility"));
		assertTrue(voiceVisibility.contains("post(refreshContentScrollStateTask)"));
		assertFalse(voiceVisibility.contains("post(refreshScrollStateTask)"));
		assertTrue(refresh.contains("if (revealActiveItem) ensureActiveItemVisible()"));
	}

	private static String method(String source, String start, String end) {
		int from = source.indexOf(start);
		int to = source.indexOf(end, from + start.length());
		assertTrue("Missing method start: " + start, from >= 0);
		assertTrue("Missing method end: " + end, to > from);
		return source.substring(from, to);
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
