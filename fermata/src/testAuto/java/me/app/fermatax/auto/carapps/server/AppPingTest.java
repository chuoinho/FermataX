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
}
