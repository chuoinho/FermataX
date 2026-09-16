package me.aap.fermata.ui.fragment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ControlFragmentContractTest {
	@Test
	public void controlObservesBinderWithoutTakingPlayerBarOwnership() throws Exception {
		String source = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata/ui/fragment/ControlFragment.java")), UTF_8);
		assertFalse(source.contains("bindControlPanel("));
		assertFalse(source.contains("bindPlayPauseButton("));
		assertFalse(source.contains("bindPrevButton("));
		assertFalse(source.contains("bindNextButton("));
		assertFalse(source.contains("bindProgressBar("));
		assertFalse(source.contains("bindProgressTime("));
		assertFalse(source.contains("bindProgressTotal("));
		assertFalse(source.contains(".bound("));
		assertFalse(source.contains(".unbind("));
		assertFalse(source.contains("new MediaSessionCompat"));
		assertFalse(source.contains("new FermataMediaServiceConnection"));
		assertTrue(source.contains("binder.addBroadcastListener(this)"));
		assertTrue(source.contains("binder.removeBroadcastListener(this)"));
	}

	@Test
	public void addonOpenButtonIsRestoredAfterCompletionOrSupersession() throws Exception {
		String source = source();
		assertTrue(source.contains("pendingAddonOpen"));
		assertTrue(source.contains("restorePendingAddonOpen()"));
		assertTrue(source.contains("pendingAddonOpen = open"));
		assertTrue(source.contains("pendingAddonOpen == open"));
	}

	@Test
	public void connectionStateIsOneCompactBadgeAtTheMediaCardCorner() throws Exception {
		String layout = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res/layout/control_fragment.xml")), UTF_8);
		assertFalse(layout.contains("control_connection_detail"));
		int badge = layout.indexOf("@+id/control_connection_status");
		assertTrue(badge >= 0);
		String badgeBlock = layout.substring(badge, layout.indexOf("/>", badge));
		assertTrue(badgeBlock.contains("layout_constraintEnd_toEndOf=\"parent\""));
		assertTrue(badgeBlock.contains("layout_constraintTop_toTopOf=\"parent\""));
	}

	@Test
	public void compactStatusLabelsDoNotNeedEllipsis() throws Exception {
		String base = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res/values/control_strings.xml")), UTF_8);
		String vietnamese = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res/values-vi/control_strings.xml")), UTF_8);
		assertTrue(base.contains(">AA connected<"));
		assertTrue(base.contains(">On car screen<"));
		assertTrue(vietnamese.contains(">Đã nối AA<"));
		assertTrue(vietnamese.contains(">Trên màn xe<"));
	}

	private static String source() throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata/ui/fragment/ControlFragment.java")), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
