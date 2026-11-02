package me.app.fermatax.auto.carapps.server;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ServerLifecycleTest {
	private static final String SERVER = "app_process / me.app.fermatax.auto.carapps.server.Main 10512 331 pkg";

	@Test
	public void killsOnlyOtherServers() {
		List<Integer> res = ServerLifecycle.stalePids(Map.of(
				100, SERVER, 200, SERVER, 300, "com.android.chrome", 400, "sh -c something"), 200);
		assertEquals(List.of(100), res);
	}

	@Test
	public void nothingStaleWhenAlone() {
		assertEquals(List.of(), ServerLifecycle.stalePids(Map.of(7, SERVER), 7));
	}
}
