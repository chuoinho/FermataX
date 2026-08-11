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
				assertTrue(layoutName + " " + id, block.contains("android:layout_height=\"64dp\""));
				assertTrue(layoutName + " " + id,
						block.contains("app:layout_constraintHorizontal_weight=\"1\""));
				assertTrue(layoutName + " " + id, block.contains("android:paddingTop=\"16dp\""));
				assertTrue(layoutName + " " + id, block.contains("android:paddingBottom=\"16dp\""));
				int index = layout.indexOf("@+id/" + id);
				assertTrue(index > previous);
				previous = index;
			}
			assertTrue(viewBlock(layout, "control_prev").contains(
					"app:layout_constraintStart_toStartOf=\"parent\""));
			assertTrue(viewBlock(layout, "control_next").contains(
					"app:layout_constraintEnd_toEndOf=\"parent\""));
			assertTrue(viewBlock(layout, "control_favorite").contains(
					"app:layout_constraintTop_toTopOf=\"parent\""));
		}
	}

	@Test
	public void noSeekConstraintLayoutRemainsAnActiveDependency() throws Exception {
		String seek = source("ui/view/ControlPanelSeekView.java");
		assertTrue(seek.contains("load(R.layout.control_panel_view2)"));
		assertTrue(Files.isRegularFile(repositoryRoot().resolve(
				"fermata/src/main/res/layout/control_panel_view2.xml")));
	}

	@Test
	public void videoUsesRuntimeOverlayConstraintsAndNonInteractiveScrim() throws Exception {
		String panel = source("ui/view/ControlPanelView.java");
		String presentation = source("ui/view/ControlPanelPresentationView.java");
		assertTrue(presentation.contains("videoHostConstraints"));
		assertTrue(presentation.contains("constraints.clear(R.id.control_panel, ConstraintSet.TOP)"));
		assertTrue(presentation.contains("ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM"));
		assertTrue(presentation.contains("body.bottomToTop = ConstraintLayout.LayoutParams.UNSET"));
		assertTrue(presentation.contains("body.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID"));
		assertTrue(presentation.contains(
				"panel.setBackgroundResource(R.drawable.control_panel_video_panel_bg)"));
		assertTrue(resource("drawable/aa_play_button_bg_automotive.xml").contains(
				"android:insetLeft=\"56dp\""));
		assertTrue(panel.contains("presentationView.setVideoMode(true)"));
		assertTrue(panel.contains("presentationView.setVideoMode(false)"));
		for (String name : new String[]{"main_activity_left.xml", "main_activity_right.xml"}) {
			String layout = resource("layout/" + name);
			assertTrue(layout.contains("@+id/control_panel_scrim"));
			assertTrue(layout.contains("android:clickable=\"false\""));
			assertTrue(layout.contains("android:visibility=\"gone\""));
		}
	}

	@Test
	public void autoVideoModeUsesReducerStateWhilePhoneKeepsItsLegacyState() throws Exception {
		String panel = source("ui/view/ControlPanelView.java");
		assertFalse(panel.contains("MASK_VIDEO_MODE"));
		assertTrue(panel.contains("presentationCoordinator.getState().videoMode()"));
		assertTrue(panel.contains("phoneVideoMode"));
		assertTrue(panel.contains("Math.max(buttonSize, toIntPx(getContext(), 64))"));
	}

	private static String viewBlock(String layout, String id) {
		int from = layout.indexOf("@+id/" + id);
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
