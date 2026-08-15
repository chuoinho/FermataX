package me.aap.fermata.media.engine;

import static me.aap.utils.async.Completed.completed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;

import android.content.Context;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;

public class MediaEngineBaseLifecycleTest {
	@Test
	public void stoppedDoesNotReleaseVideoOutput() throws Exception {
		TestEngine engine = new TestEngine();
		VideoView view = view();
		engine.setVideoView(view);

		engine.resetPlaybackState();

		assertSame(view, engine.boundVideoView());
	}

	@Test
	public void closeReleasesVideoOutput() throws Exception {
		TestEngine engine = new TestEngine();
		engine.setVideoView(view());

		engine.close();

		assertNull(engine.boundVideoView());
	}

	@Test
	public void stoppedRemovesSubtitleConsumerBeforeTeardown() throws Exception {
		TestEngine engine = new TestEngine();
		TrackingVideoView view = trackingView();
		engine.setVideoView(view);
		engine.addSubtitleConsumer(view);

		engine.resetPlaybackState();

		assertSame(view, engine.boundVideoView());
		assertEquals(1, view.releaseCount);
	}

	@Test
	public void engineKeepsViewAcrossPrepare() throws Exception {
		TestEngine engine = new TestEngine();
		VideoView view = view();
		engine.setVideoView(view);

		engine.prepare(null);

		assertSame(view, engine.boundVideoView());
	}

	private static VideoView view() throws Exception {
		return allocate(VideoView.class);
	}

	private static TrackingVideoView trackingView() throws Exception {
		return allocate(TrackingVideoView.class);
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return type.cast(unsafeType.getMethod("allocateInstance", Class.class)
				.invoke(unsafe, type));
	}

	private static final class TrackingVideoView extends VideoView {
		int releaseCount;

		private TrackingVideoView() {
			super((Context) null);
		}

		@Override
		public void clearSubtitleSurface() {
		}

		@Override
		public void prepareSubDrawer(boolean dbl) {
		}

		@Override
		public void releaseSubDrawer() {
			releaseCount++;
		}
	}

	private static final class TestEngine extends MediaEngineBase {
		private PlayableItem source;

		TestEngine() {
			super(Listener.DUMMY);
		}

		@Override
		public int getId() {
			return 0;
		}

		@Override
		public void prepare(PlayableItem source) {
			stopped(false);
			this.source = source;
		}

		@Override
		public void start() {
			started();
		}

		@Override
		public void stop() {
			stopped(false);
		}

		@Override
		public void pause() {
			stopped(true);
		}

		@Override
		public PlayableItem getSource() {
			return source;
		}

		@Override
		public FutureSupplier<Long> getDuration() {
			return completed(0L);
		}

		@Override
		public FutureSupplier<Long> getPosition() {
			return completed(0L);
		}

		@Override
		public void setPosition(long position) {
		}

		@Override
		public FutureSupplier<Float> getSpeed() {
			return completed(1F);
		}

		@Override
		public void setSpeed(float speed) {
		}

		@Override
		public float getVideoWidth() {
			return 0;
		}

		@Override
		public float getVideoHeight() {
			return 0;
		}

		void resetPlaybackState() {
			stopped(false);
		}

		VideoView boundVideoView() {
			return videoView;
		}
	}
}
