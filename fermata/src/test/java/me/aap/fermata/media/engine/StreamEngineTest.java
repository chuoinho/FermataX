package me.aap.fermata.media.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.content.Context;

import java.lang.reflect.Method;
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

	@Test
	public void forwardsInnerAudioSessionChangesAsWrapperEngine() throws Exception {
		FakeProvider provider = new FakeProvider();
		RecordingListener outer = new RecordingListener();
		StreamEngine wrapper = new StreamEngine(provider, outer);

		provider.emitAudioSessionIdChanged(42);

		assertEquals(1, outer.audioSessionChangeCount);
		assertEquals(42, outer.audioSessionId);
		assertSame(wrapper, outer.audioSessionChangeEngine);
		assertNotSame(provider.engine, outer.audioSessionChangeEngine);
	}

	private static final class RecordingListener implements MediaEngine.Listener {
		private int firstFrameCount;
		private MediaEngine firstFrameEngine;
		private int audioSessionChangeCount;
		private int audioSessionId;
		private MediaEngine audioSessionChangeEngine;

		@Override
		public void onVideoFirstFrame(MediaEngine engine) {
			firstFrameCount++;
			firstFrameEngine = engine;
		}

		public void onEngineAudioSessionIdChanged(MediaEngine engine, int audioSessionId) {
			audioSessionChangeCount++;
			audioSessionChangeEngine = engine;
			this.audioSessionId = audioSessionId;
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

		private void emitAudioSessionIdChanged(int audioSessionId) throws Exception {
			Method callback = MediaEngine.Listener.class.getMethod(
					"onEngineAudioSessionIdChanged", MediaEngine.class, int.class);
			callback.invoke(listener, engine, audioSessionId);
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
