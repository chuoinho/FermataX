package me.aap.fermata.ui.smarttop;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source/resource guards for the strict SmartTop background ownership boundary. */
public class SmartTopBackgroundContractTest {
	@Test
	public void foregroundIconCanNeverBecomeArtwork() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(binder.contains("ImageView sourceIcon = views.sourceIcon()"));
		assertTrue(binder.contains("sourceIcon.setImageResource(state.icon())"));
		assertFalse(binder.contains("setImageBitmap("));
		assertFalse(binder.contains("getIconUri()"));
		assertTrue(binder.contains("root.setBackground(rendered.ripple())"));
	}

	@Test
	public void artworkUsesDirectMetadataAndCacheOnlyLoader() throws Exception {
		String resolver = source("ui/smarttop/SmartTopArtworkResolver.java");
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(resolver.contains("METADATA_KEY_ALBUM_ART_URI"));
		assertFalse(coordinator.contains("getIconUri()"));
		assertTrue(binder.contains("getBitmapIfCached("));
		assertFalse(binder.contains(".getBitmap("));
	}

	@Test
	public void cacheOnlyHttpMissCannotReachTransport() throws Exception {
		String cache = source("media/engine/BitmapCache.java");
		int start = cache.indexOf("getBitmapIfCached(");
		int end = cache.indexOf("private Bitmap getCachedBitmap", start);
		String method = cache.substring(start, end);
		assertTrue(method.contains("if (!originalFile.isFile()) return completedNull()"));
		assertFalse(method.contains("loadHttpBitmap("));
		assertFalse(method.contains("downloadImage("));
		assertFalse(method.contains("loadUriBitmap("));
		assertFalse(method.contains("getVfsManager("));
	}

	@Test
	public void timelinePayloadDoesNotTouchBackgroundOrArtwork() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		int start = binder.indexOf("public void bindTimelineUpdate(");
		int end = binder.indexOf("private void bindActions(", start);
		String method = binder.substring(start, end);
		assertFalse(method.contains("bindBackground"));
		assertFalse(method.contains("bindSourceIcon"));
		assertFalse(method.contains("getMediaData"));
		assertFalse(method.contains("getBitmap"));
	}

	@Test
	public void staleGuardIncludesGenerationItemAndBackgroundIdentity() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(binder.contains(
				"record BackgroundBindToken(long generation, String itemId, String backgroundIdentity)"));
		assertTrue(binder.contains("background.identity()"));
		assertTrue(binder.contains("current == rendered.content()"));
	}

	@Test
	public void stateCopiesPreserveBackground() throws Exception {
		String state = source("ui/smarttop/SmartTopViewState.java");
		assertEquals(4, occurrences(state, "icon, background, eyebrow"));
		assertTrue(state.contains("public SmartTopViewState withBackground("));
	}

	@Test
	public void v2LayoutHasNoNewBackgroundChildOrConstraint() throws Exception {
		String layout = resource("layout/dashboard_smart_top_v2_item.xml");
		assertEquals(1, occurrences(layout, "<ImageView"));
		assertFalse(layout.contains("dashboard_smart_background"));
		assertTrue(layout.contains("android:background=\"@drawable/dashboard_smart_top_bg\""));
	}

	@Test
	public void spectrumNeedsExplicitAudioEvidence() throws Exception {
		String resolver = source("ui/smarttop/SmartTopArtworkResolver.java");
		String coordinator = source("ui/smarttop/SmartTopCoordinator.java");
		assertTrue(resolver.contains("AddonCapability.RADIO"));
		assertTrue(resolver.contains("AddonCapability.PODCAST"));
		assertTrue(resolver.contains("AddonCapability.AUDIOBOOK"));
		assertTrue(coordinator.contains("isProvenAudioRoot(item.getRoot())"));
		assertTrue(coordinator.contains("getAddonInfo(candidate.addonClass())"));
		assertTrue(coordinator.contains("SmartTopBackground.audioSpectrum(candidate.addonClass())"));
		assertFalse(coordinator.contains("false, false, !candidate.video()"));
		assertFalse(coordinator.contains("!item.isVideo()"));
		String radioRoot = repositorySource(
				"modules/radio/src/main/java/me/aap/fermata/addon/radio/RadioRootItem.java");
		assertTrue(radioRoot.contains("super(ID, lib, AddonCapability.RADIO)"));
	}

	@Test
	public void recentIconsStayBlueAcrossEveryItemState() throws Exception {
		String binder = source("ui/smarttop/SmartTopBinder.java");
		assertTrue(binder.contains("view.setCompoundDrawableTintList("));
		assertTrue(binder.contains("R.color.dashboard_smart_recent_icon_tint"));
		assertTrue(binder.contains("view.setCompoundDrawableTintList(null)"));
		String tint = resource("color/dashboard_smart_recent_icon_tint.xml");
		assertTrue(tint.contains("android:color=\"#7AA7FF\""));
		assertFalse(tint.contains("state_activated"));
	}

	@Test
	public void artworkBackgroundIsSoftenedAndDarkenedWithoutTouchingForeground() throws Exception {
		String drawable = source("ui/smarttop/SmartTopCardBackgroundDrawable.java");
		assertTrue(drawable.contains("ARTWORK_SOFT_EDGE_PX = 48"));
		assertTrue(drawable.contains("softenedArtwork = soften(bitmap)"));
		assertTrue(drawable.contains("ARTWORK_DIM_COLOR = 0x70000000"));
		assertTrue(drawable.contains("drawArtworkDim(canvas)"));
		assertTrue(drawable.contains("ARTWORK_SCRIM_START = 0xCC070B11"));
		assertTrue(drawable.contains("ARTWORK_SCRIM_END = 0xF0070B11"));
	}

	private static int occurrences(String text, String value) {
		int count = 0;
		for (int at = 0; (at = text.indexOf(value, at)) >= 0; at += value.length()) count++;
		return count;
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static String resource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res").resolve(relativePath)), UTF_8);
	}

	private static String repositorySource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
