package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;

public class MediaEngineShutdownTest {
	@Test
	public void stopFailureDoesNotBlockAudioFocusOrClose() {
		List<String> calls = new ArrayList<>();
		MediaEngine engine = (MediaEngine) Proxy.newProxyInstance(
				MediaEngine.class.getClassLoader(), new Class<?>[]{MediaEngine.class},
				(proxy, method, args) -> {
					String name = method.getName();
					if (name.equals("stop") || name.equals("releaseAudioFocus") || name.equals("close"))
						calls.add(name);
					if (name.equals("stop")) throw new IllegalStateException("expected");
					if (method.getReturnType() == boolean.class) return false;
					if (method.getReturnType() == int.class) return 0;
					if (method.getReturnType() == long.class) return 0L;
					if (method.getReturnType() == float.class) return 0f;
					return null;
				});

		MediaEngineShutdown.release(engine, null, null);

		assertEquals(List.of("stop", "releaseAudioFocus", "close"), calls);
	}
}
