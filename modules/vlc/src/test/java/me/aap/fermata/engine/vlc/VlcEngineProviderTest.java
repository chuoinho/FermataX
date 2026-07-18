package me.aap.fermata.engine.vlc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.util.List;

import org.junit.Test;

public class VlcEngineProviderTest {
	@Test
	public void libVlcReceivesMutableDefensiveOptionsCopy() {
		List<String> source = List.of("--network-caching=60000");
		List<String> copy = VlcEngineProvider.mutableOptions(source);

		assertNotSame(source, copy);
		copy.add("--no-stats");
		assertEquals(List.of("--network-caching=60000"), source);
	}
}
