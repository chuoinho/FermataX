package me.aap.fermata.ui.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ControlPanelContractTest {
	private static final String[] TRANSPORT = {
			"control_prev", "control_rw", "control_play_pause", "control_ff", "control_next"};

	@Test
	public void fiveTransportButtonsKeepOneCenteredWeightedChainAndCarSafeHeight() throws Exception {
		for (String layoutName : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String layout = resource("layout/" + layoutName);
			int previous = -1;
			for (String id : TRANSPORT) {
				String block = viewBlock(layout, id);
				assertTrue(layoutName + " " + id, block.contains("android:layout_width=\"0dp\""));
				assertTrue(layoutName + " " + id, block.contains(
						"android:layout_height=\"@dimen/control_panel_transport_height\""));
				assertTrue(layoutName + " " + id,
						block.contains("app:layout_constraintHorizontal_weight=\"1\""));
				assertTrue(layoutName + " " + id, block.contains("android:padding=\"16dp\""));
				int index = layout.indexOf("@+id/" + id);
				assertTrue(index > previous);
				previous = index;
			}
			assertTrue(viewBlock(layout, "control_prev").contains(
					"app:layout_constraintStart_toStartOf=\"@id/control_panel_transport_start\""));
			assertTrue(viewBlock(layout, "control_next").contains(
					"app:layout_constraintEnd_toEndOf=\"@id/control_panel_transport_end\""));
			assertTrue(layout.contains("app:layout_constraintGuide_percent=\"0.19\""));
			assertTrue(layout.contains("app:layout_constraintGuide_percent=\"0.81\""));
			assertFalse(layout.contains("androidx.constraintlayout.widget.Barrier"));
		}
	}

	@Test
	public void panelGeometryIsOneFixed76DpLayoutInEveryState() throws Exception {
		String dimensions = resource("values/dimens.xml");
		assertTrue(dimensions.contains("name=\"control_panel_height\">76dp"));
		assertTrue(dimensions.contains("name=\"control_panel_transport_height\">64dp"));
		assertTrue(dimensions.contains("name=\"control_panel_seek_track_height\">3dp"));
		for (String layoutName : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String layout = resource("layout/" + layoutName);
			assertTrue(layout.contains("android:layout_height=\"@dimen/control_panel_height\""));
			assertTrue(layout.contains("android:maxHeight=\"@dimen/control_panel_seek_track_height\""));
			assertTrue(viewBlock(layout, "show_hide_bars").contains(
					"app:layout_constraintStart_toStartOf=\"parent\""));
			assertTrue(viewBlock(layout, "control_menu_button").contains(
					"app:layout_constraintEnd_toEndOf=\"parent\""));
		}
		String panel = source("ui/view/ControlPanelView.java");
		assertFalse(panel.contains("applyDriverSideControls"));
		assertTrue(panel.contains("R.dimen.control_panel_height"));
	}

	@Test
	public void edgeActionsScaleVisuallyWithoutChangingPanelOrTouchGeometry() throws Exception {
		String dimensions = resource("values/dimens.xml");
		String wideDimensions = resource("values-w840dp/dimens.xml");
		String panel = source("ui/view/ControlPanelView.java");
		assertTrue(dimensions.contains("name=\"control_panel_edge_action_size\">44dp"));
		assertTrue(dimensions.contains("name=\"control_panel_edge_action_padding\">8dp"));
		assertTrue(wideDimensions.contains("name=\"control_panel_edge_action_size\">48dp"));
		assertTrue(panel.contains("R.dimen.control_panel_edge_action_size"));
		assertTrue(panel.contains("setEdgeActionSize(R.id.show_hide_bars_icon"));
		assertTrue(panel.contains("setEdgeActionSize(R.id.control_menu_button_icon"));
		assertFalse(panel.contains("Math.min(toIntPx(getContext(), 32)"));
		for (String layoutName : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String layout = resource("layout/" + layoutName);
			assertTrue(viewBlock(layout, "show_hide_bars").contains(
					"android:layout_height=\"@dimen/control_panel_transport_height\""));
			assertTrue(viewBlock(layout, "control_menu_button").contains(
					"android:layout_height=\"@dimen/control_panel_transport_height\""));
		}
	}

	@Test
	public void liveModeUsesAStableBadgeAndPreservesTransportGeometry() throws Exception {
		String binder = source("media/service/FermataServiceUiBinder.java");
		assertTrue(binder.contains("bindLiveBadge"));
		assertTrue(binder.contains("liveBadge.setVisibility(VISIBLE)"));
		assertFalse(binder.contains("progressTime.setText(R.string.playback_live)"));
		assertTrue(binder.contains("rwButton.setVisibility(INVISIBLE)"));
		assertTrue(binder.contains("ffButton.setVisibility(INVISIBLE)"));
		for (String layoutName : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String layout = resource("layout/" + layoutName);
			String badge = viewBlock(layout, "live_badge");
			assertTrue(badge.contains("android:visibility=\"gone\""));
			assertTrue(layout.contains("android:letterSpacing=\"0.08\""));
			assertTrue(layout.contains("android:textColor=\"#FF6B63\""));
			assertFalse(layout.contains("android:animation"));
		}
		assertTrue(resource("drawable/control_panel_live_dot.xml").contains(
				"android:color=\"#F4574F\""));
	}

	@Test
	public void videoScrimIsOnly16DpTallerThanPanelAndFadesTo84PercentBlack()
			throws Exception {
		String dimensions = resource("values/dimens.xml");
		assertTrue(dimensions.contains("name=\"control_panel_scrim_height\">92dp"));
		String scrim = resource("drawable/control_panel_video_scrim.xml");
		assertTrue(scrim.contains("<gradient"));
		assertTrue(scrim.contains("android:startColor=\"#00000000\""));
		assertTrue(scrim.contains("android:endColor=\"#D6000000\""));
		for (String name : new String[]{"main_activity_left.xml", "main_activity_right.xml"}) {
			assertTrue(resource("layout/" + name).contains(
					"android:layout_height=\"@dimen/control_panel_scrim_height\""));
		}
		String presentation = source("ui/view/ControlPanelPresentationView.java");
		assertTrue(presentation.contains("videoMode && (visibility == VISIBLE)"));
	}

	@Test
	public void overlayInsetsEveryAttachedRecyclerViewAndHiddenPanelsStayGone()
			throws Exception {
		String coordinator = source("ui/view/ControlPanelContentInsetCoordinator.java");
		assertTrue(coordinator.contains("view instanceof RecyclerView list"));
		assertTrue(coordinator.contains("boolean clip = (insetBottom == 0)"));
		assertTrue(coordinator.contains("setClipToPadding(clip)"));
		assertTrue(coordinator.contains("addOnGlobalLayoutListener(this)"));
		assertTrue(coordinator.contains("R.dimen.control_panel_height"));
		String presentation = source("ui/view/ControlPanelPresentationView.java");
		assertTrue(presentation.contains("contentInsets.setPanelVisible"));
		String panel = source("ui/view/ControlPanelView.java");
		assertTrue(panel.contains("setPanelVisibility(state.controlsVisible() ? VISIBLE : GONE)"));
		assertFalse(panel.contains("setPanelVisibility(INVISIBLE)"));
	}

	@Test
	public void noSeekConstraintLayoutRemainsAnActiveDependency() throws Exception {
		String seek = source("ui/view/ControlPanelSeekView.java");
		assertTrue(seek.contains("load(R.layout.control_panel_view2)"));
		assertTrue(Files.isRegularFile(repositoryRoot().resolve(
				"fermata/src/main/res/layout/control_panel_view2.xml")));
	}

	@Test
	public void everyModeUsesStaticOverlayConstraintsAndVideoHasNonInteractiveScrim() throws Exception {
		String panel = source("ui/view/ControlPanelView.java");
		String presentation = source("ui/view/ControlPanelPresentationView.java");
		assertFalse(presentation.contains("ConstraintSet"));
		assertFalse(presentation.contains("body.bottomToTop"));
		assertTrue(presentation.contains(
				"panel.setBackgroundResource(R.drawable.control_panel_video_panel_bg)"));
		assertTrue(resource("drawable/aa_play_button_bg_automotive.xml").contains(
				"android:insetLeft=\"8dp\""));
		assertTrue(panel.contains("presentationView.setVideoMode(true)"));
		assertTrue(panel.contains("presentationView.setVideoMode(false)"));
		for (String name : new String[]{"main_activity_left.xml", "main_activity_right.xml"}) {
			String layout = resource("layout/" + name);
			String body = viewBlock(layout, "body_layout");
			String controls = viewBlock(layout, "control_panel");
			assertTrue(body.contains("app:layout_constraintBottom_toBottomOf=\"parent\""));
			assertFalse(body.contains("layout_constraintBottom_toTopOf=\"@id/control_panel\""));
			assertTrue(controls.contains("app:layout_constraintBottom_toBottomOf=\"parent\""));
			assertFalse(controls.contains("layout_constraintTop_toBottomOf=\"@id/body_layout\""));
			assertTrue(controls.contains("android:translationZ=\"4dp\""));
			assertTrue(layout.contains("@+id/control_panel_scrim"));
			assertTrue(layout.contains("android:clickable=\"false\""));
			assertTrue(layout.contains("android:visibility=\"gone\""));
		}
	}

	@Test
	public void everyVideoHostUsesReducerOwnedPresentationState() throws Exception {
		String panel = source("ui/view/ControlPanelView.java");
		assertFalse(panel.contains("MASK_VIDEO_MODE"));
		assertTrue(panel.contains("presentationCoordinator.getState().videoMode()"));
		assertFalse(panel.contains("phoneVideoMode"));
		assertFalse(panel.contains("PHONE_VIDEO_MODE"));
		assertFalse(panel.contains("class HideTimer"));
		assertTrue(panel.contains("presentationCoordinator.enterVideo"));
		assertTrue(panel.contains("presentationCoordinator.leaveVideo"));
		assertTrue(panel.contains("presentationCoordinator.toggleControls"));
		assertTrue(panel.contains("presentationCoordinator.refreshTimeout"));
		assertTrue(panel.contains("private void applyPresentation(State state)"));
		assertTrue(panel.contains("R.dimen.control_panel_transport_height"));
		assertFalse(panel.contains("Math.max(buttonSize"));
	}

	@Test
	public void phonePlayerHasNoEdgeHideActionWhileAutomotiveOwnsPlayerBack() throws Exception {
		String panel = source("ui/view/ControlPanelView.java");
		assertTrue(panel.contains("if (isAutoUi(a)) {"));
		assertTrue(panel.contains("bindBackControl(g);"));
		assertTrue(panel.contains("bindBackControl(seekTime);"));
		assertTrue(panel.contains("disableBackControl(g);"));
		assertTrue(panel.contains("disableBackControl(seekTime);"));
		assertTrue(panel.contains("showHideBars.setVisibility(GONE);"));
		assertTrue(panel.contains("performAutoPlayerBack(a)"));
		assertFalse(panel.contains("a.setBarsHidden(!a.isBarsHidden())"));
		assertFalse(panel.contains("R.drawable.expand"));
		assertTrue(panel.contains("showHideBars.setImageResource(me.aap.utils.R.drawable.back)"));
		assertTrue(panel.contains("private boolean isAutoBackTouch(MotionEvent e)"));
		assertTrue(panel.contains("if (!isAutoUi(a)) return false;"));
		assertFalse(panel.contains("isPlayerBackPresentation"));
	}

	@Test
	public void videoInfoBarRemainsAbsentOnEveryHost() throws Exception {
		String info = source("ui/view/VideoInfoView.java");
		String video = source("ui/view/VideoView.java");
		String panel = source("ui/view/ControlPanelView.java");
		assertTrue(info.contains("super.setVisibility(GONE);"));
		assertTrue(video.contains("new FrameLayout.LayoutParams(MATCH_PARENT, 0)"));
		assertTrue(panel.contains("if (info != null) info.setVisibility(GONE);"));
	}

	private static String viewBlock(String layout, String id) {
		int from = layout.indexOf("@+id/" + id + "\"");
		int to = layout.indexOf("/>", from);
		assertTrue("Missing " + id, from >= 0);
		assertTrue("Unterminated " + id, to > from);
		return layout.substring(from, to);
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static String resource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve("fermata/src/main/res")
				.resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
