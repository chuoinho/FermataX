package me.aap.fermata.media.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.content.Context;

import java.lang.reflect.Proxy;

import org.junit.Test;

public class StreamEngineTest {
	@Test
	public void forwardsInnerFirstFrameAsWrapperEngine() {
		FakeProvider provider = new FakeProvider();
		RecordingListener outer = new RecordingListener();
		StreamEngine wrapper = new StreamEngine(provider, outer);

		provider.emitFirstFrame();

		assertEquals(1, outer.firstFrameCount);
		assertSame(wrapper, outer.firstFrameEngine);
		assertNotSame(provider.engine, outer.firstFrameEngine);
	}

	private static final class RecordingListener implements MediaEngine.Listener {
		private int firstFrameCount;
		private MediaEngine firstFrameEngine;

		@Override
		public void onVideoFirstFrame(MediaEngine engine) {
			firstFrameCount++;
			firstFrameEngine = engine;
		}
	}

	private static final class FakeProvider implements MediaEngineProvider {
		private final MediaEngine engine = (MediaEngine) Proxy.newProxyInstance(
				MediaEngine.class.getClassLoader(), new Class<?>[]{MediaEngine.class},
				(proxy, method, args) -> defaultValue(method.getReturnType()));
		private MediaEngine.Listener listener;

		@Override
		public void init(Context context) {
		}

		@Override
		public MediaEngine createEngine(MediaEngine.Listener listener) {
			this.listener = listener;
			return engine;
		}

		private void emitFirstFrame() {
			listener.onVideoFirstFrame(engine);
		}
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive() || (type == void.class)) return null;
		if (type == boolean.class) return false;
		if (type == char.class) return '\0';
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0f;
		return 0d;
	}
}
