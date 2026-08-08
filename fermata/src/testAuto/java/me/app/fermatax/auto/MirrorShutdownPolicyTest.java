package me.app.fermatax.auto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class MirrorShutdownPolicyTest {
	@Test
	public void mirrorDoesNotUseLongLivedManualWakeLock() throws IOException {
		String source = source("MirrorDisplay.java");
		assertFalse(source.contains("SCREEN_DIM_WAKE_LOCK"));
		assertFalse(source.contains("ACQUIRE_CAUSES_WAKEUP"));
		assertFalse(source.contains("newWakeLock"));
		assertTrue(source.contains("SURFACE_IDLE_GRACE_MS"));
		assertTrue(source.contains("suspendSurfaceResources"));
	}

	@Test
	public void surfaceDestroyUsesCallbackSurfaceAndReleasesIt() throws IOException {
		String source = source("MirrorServiceFS.java");
		int method = source.indexOf("void onSurfaceDestroyed(@NonNull SurfaceContainer sc)");
		int next = source.indexOf("@Override", method + 1);
		String body = source.substring(method, next);
		assertTrue(body.contains("releaseSurface(sc)"));
		assertTrue(body.contains("MirrorServiceFS.sc == sc"));
		assertFalse(body.contains("releaseSurface(MirrorServiceFS.sc)"));
	}

	private static String source(String name) throws IOException {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while ((current != null) && !Files.isRegularFile(current.resolve("settings.gradle")))
			current = current.getParent();
		if (current == null) throw new IllegalStateException("Unable to locate project root");
		return new String(Files.readAllBytes(current.resolve(
				"fermata/src/auto/java/me/app/fermatax/auto/" + name)), UTF_8);
	}
}
