package me.app.fermatax.auto.carapps.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class AppPingTest {

	@Test
	public void aPingFromBeforeTheStartIsNotAnsweredAgain() throws Exception {
		File f = Files.createTempFile("carapps", ".ping").toFile();
		try {
			f.setLastModified(1_000_000L);
			AppPing ping = new AppPing(f);
			assertFalse("the start handed the binder over already", ping.pinged());

			f.setLastModified(2_000_000L);
			assertTrue("the app wrote it again: hand the binder over", ping.pinged());
			assertFalse("once per ping", ping.pinged());

			f.setLastModified(3_000_000L);
			assertTrue(ping.pinged());
		} finally {
			f.delete();
		}
	}

	@Test
	public void noFileIsNoPing() throws Exception {
		File f = Files.createTempFile("carapps", ".ping").toFile();
		AppPing ping = new AppPing(f);
		assertTrue(f.delete());
		assertFalse("a removed file (app data cleared) asks for nothing", ping.pinged());
		assertFalse(new AppPing(new File(f.getParentFile(), "none")).pinged());
	}

	@Test
	public void theFileIsTheAppsExternalFilesDirForItsUser() {
		assertEquals("/storage/emulated/0/Android/data/me.app.fermataX.auto/files/" + AppPing.FILE,
				AppPing.fileFor(10512, "me.app.fermataX.auto").getPath().replace('\\', '/'));
		assertEquals("/storage/emulated/10/Android/data/p/files/" + AppPing.FILE,
				AppPing.fileFor(1010512, "p").getPath().replace('\\', '/'));
	}

	private static File tempDir() throws Exception {
		File d = Files.createTempDirectory("carapps").toFile();
		d.deleteOnExit();
		return d;
	}

	/**
	 * The server takes its baselines before the startup handoff; the app, turned off, writes the stop file inside that
	 * very handoff call. That stop is newer than the baseline, so it wins.
	 */
	@Test
	public void aStopWrittenDuringTheStartupHandoffEndsTheServer() throws Exception {
		File dir = tempDir();
		File ping = new File(dir, AppPing.FILE);
		File stop = new File(dir, AppPing.STOP_FILE);
		AppPing p = new AppPing(ping);
		java.util.List<String> ran = new java.util.ArrayList<>();

		Files.write(stop.toPath(), new byte[0]); // no content is ever read: an empty or unreadable file stops too
		stop.setLastModified(2_000_000L);
		p.check(() -> ran.add("handoff"), () -> ran.add("stop"));
		assertEquals(java.util.List.of("stop"), ran);
	}

	@Test
	public void aStopFromBeforeTheStartIsNotThisServers() throws Exception {
		File dir = tempDir();
		File stop = new File(dir, AppPing.STOP_FILE);
		Files.write(stop.toPath(), new byte[]{1});
		stop.setLastModified(1_000_000L);
		AppPing p = new AppPing(new File(dir, AppPing.FILE));
		java.util.List<String> ran = new java.util.ArrayList<>();
		p.check(() -> ran.add("handoff"), () -> ran.add("stop"));
		assertTrue(ran.isEmpty());
	}

	/** The ping is for the handoff only, whatever it holds (an older app wrote "stop" into it): no content is read. */
	@Test
	public void thePingStillHandsOverAndItsContentMeansNothing() throws Exception {
		File dir = tempDir();
		File ping = new File(dir, AppPing.FILE);
		AppPing p = new AppPing(ping);
		java.util.List<String> ran = new java.util.ArrayList<>();
		Files.write(ping.toPath(), "stop".getBytes());
		ping.setLastModified(2_000_000L);
		p.check(() -> ran.add("handoff"), () -> ran.add("stop"));
		Files.write(ping.toPath(), "1".getBytes());
		ping.setLastModified(3_000_000L);
		p.check(() -> ran.add("handoff"), () -> ran.add("stop"));
		assertEquals(java.util.List.of("handoff", "handoff"), ran);
	}
}
