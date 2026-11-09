package me.app.fermatax.auto.carapps.server;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ServerLifecycleTest {
	private static final String SERVER = "app_process / me.app.fermatax.auto.carapps.server.Main 10512 331 pkg";

	@Test
	public void killsOnlyOtherServers() {
		List<Integer> res = ServerLifecycle.stalePids(Map.of(
				100, SERVER, 200, SERVER, 300, "com.android.chrome", 400, "sh -c something"), Map.of(), 200);
		assertEquals(List.of(100), res);
	}

	@Test
	public void nothingStaleWhenAlone() {
		assertEquals(List.of(), ServerLifecycle.stalePids(Map.of(7, SERVER), Map.of(), 7));
	}

	/** Measured over wireless ADB: the launching shell's command line holds both markers and the server killed it. */
	@Test
	public void neverKillsTheLaunchingShellOrAnyAncestor() {
		String launcher = "sh -c CLASSPATH=/data/app/base.apk app_process / me.app.fermatax.auto.carapps.server.Main 10512 331 pkg"
				+ " >/dev/null 2>&1";
		Map<Integer, String> cmdlines = new HashMap<>();
		cmdlines.put(1, "init");
		cmdlines.put(900, "/apex/com.android.adbd/bin/adbd");
		cmdlines.put(950, launcher);
		cmdlines.put(960, "/system/bin/sh -c " + launcher); // an outer wrapper holding the markers as well
		cmdlines.put(970, SERVER); // self
		cmdlines.put(500, SERVER); // a real stale server, reparented to init
		cmdlines.put(510, SERVER); // another one, still under a live process that is not an ancestor
		Map<Integer, Integer> parents = Map.of(970, 950, 950, 960, 960, 900, 900, 1, 500, 1, 510, 900);

		List<Integer> res = ServerLifecycle.stalePids(cmdlines, parents, 970);

		assertEquals(List.of(500, 510), res.stream().sorted().collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void aCycleInTheProcessTableDoesNotHang() {
		assertEquals(List.of(), ServerLifecycle.stalePids(Map.of(7, SERVER, 8, SERVER), Map.of(7, 8, 8, 7), 7));
	}
}
