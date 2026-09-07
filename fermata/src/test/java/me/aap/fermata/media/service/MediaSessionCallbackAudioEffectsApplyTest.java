package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.media.audio.AudioEffectsController;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.engine.EngineSelection;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.ProgressiveResultConsumer;
import me.aap.utils.pref.BasicPreferenceStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class MediaSessionCallbackAudioEffectsApplyTest {
	@Test
	public void initialOnlyApplyCreatesFreshBackendWithLatestProfileAndPreservesPausedPosition()
			throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PAUSED, "INITIAL_ONLY",
				true);
		AudioEffectsProfile next = enabledProfile(-9);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);
		assertFalse(result.isDone());

		h.position.complete(12_345L);
		assertNotSame(h.current, h.candidate);
		assertEquals(1, h.backends.get(0).releaseCount);
		assertEquals(1, h.candidate.prepareCount);

		h.callback.onEnginePrepared(h.candidate);

		assertTrue(result.isDone());
		assertFalse(result.isFailed());
		assertEquals(12_345L, h.candidate.lastPosition);
		assertEquals(0, h.candidate.startCount);
		assertEquals(next, h.backends.get(1).lastProfile);
	}

	@Test
	public void initialOnlyApplyReplacesAnActiveCurveWithSavedZeroCurve() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PAUSED, "INITIAL_ONLY", true);
		AudioEffectsProfile active = profileWithCurve(9);
		h.repository.save(active);
		AudioEffectsProfile next = enabledProfile(0);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);
		h.position.complete(1_000L);
		h.callback.onEnginePrepared(h.candidate);

		assertTrue(result.isDoneNotFailed());
		assertEquals(0, h.backends.get(1).lastProfile.canonicalCurveDb()[5]);
		assertEquals(1, h.backends.get(0).releaseCount);
	}

	@Test
	public void initialOnlyApplyPreservesPlayingIntent() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PLAYING, "INITIAL_ONLY",
				true);
		AudioEffectsProfile next = enabledProfile(-5);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);
		h.position.complete(7_000L);
		h.callback.onEnginePrepared(h.candidate);

		assertTrue(result.isDoneNotFailed());
		assertEquals(1, h.candidate.startCount);
		assertEquals(7_000L, h.candidate.lastPosition);
	}

	@Test
	public void liveStreamApplyRestartsAtLiveEdge() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PLAYING, "INITIAL_ONLY",
				true, true);
		AudioEffectsProfile next = enabledProfile(-3);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);
		assertTrue(h.candidate.prepareCount == 1);
		h.callback.onEnginePrepared(h.candidate);

		assertTrue(result.isDoneNotFailed());
		assertEquals(1, h.candidate.startCount);
		assertEquals(-1L, h.candidate.lastPosition);
	}

	@Test
	public void stalePositionCallbackCannotRestartAfterSourceSwitch() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PLAYING, "INITIAL_ONLY",
				true);
		AudioEffectsProfile next = enabledProfile(-2);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);
		invoke(h.callback, "beginPlaybackRequest", new Class[]{PlayableItem.class, MediaEngine.class},
				h.other, null);
		h.position.complete(4_000L);

		assertTrue(result.isFailed());
		assertTrue(result.getFailure() instanceof CancellationException);
		assertEquals(0, h.candidate.prepareCount);
	}

	@Test
	public void stopInvalidatesPendingApplyAndCannotResurrectOldContent() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PAUSED, "INITIAL_ONLY",
				true);
		AudioEffectsProfile next = enabledProfile(-1);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);
		h.callback.stopImmediately();
		h.position.complete(4_000L);

		assertTrue(result.isFailed());
		assertTrue(result.getFailure() instanceof CancellationException);
		assertEquals(0, h.candidate.prepareCount);
		assertNull(h.callback.getEngine());
	}

	@Test
	public void standardLiveApplyUpdatesInPlaceWithoutRestart() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PLAYING, "STANDARD_LIVE",
				true);
		AudioEffectsProfile next = enabledProfile(-8);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);

		assertTrue(result.isDoneNotFailed());
		assertSame(h.current, h.callback.getEngine());
		assertEquals(0, h.current.closeCount);
		assertEquals(2, h.backends.get(0).applyCount);
		assertSame(next, h.backends.get(0).lastProfile);
	}

	@Test
	public void initialOnlyBackendFailureIsReportedAsFailure() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PAUSED, "INITIAL_ONLY",
				false);
		AudioEffectsProfile next = enabledProfile(-6);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);
		h.position.complete(2_000L);
		h.callback.onEnginePrepared(h.candidate);

		assertTrue(result.isFailed());
		assertEquals(1, h.backends.get(1).applyCount);
		assertEquals(1, h.backends.get(0).releaseCount);
	}

	@Test
	public void synchronousPositionLookupFailureReturnsFailedApply() throws Exception {
		Harness h = harness(PlaybackStateCompat.STATE_PAUSED, "INITIAL_ONLY", true);
		h.current.positionFailure = new IllegalStateException("position unavailable");
		AudioEffectsProfile next = enabledProfile(-10);
		h.callback.deferAudioEffectsProfile(next);
		h.repository.save(next);

		FutureSupplier<Void> result = h.callback.applyAudioEffects(next);

		assertTrue(result.isFailed());
		assertEquals(0, h.candidate.prepareCount);
	}

	@Test
	public void applyWithNoNativeEngineDoesNotRestart() throws Exception {
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(
				new BasicPreferenceStore());
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		set(callback, "terminal", false);
		set(callback, "audioEffectsController", createController(repository, "UNAVAILABLE", true,
				new LinkedList<>()));

		FutureSupplier<Void> result = callback.applyAudioEffects(enabledProfile(-4));

		assertTrue(result.isDoneNotFailed());
		assertNull(callback.getEngine());
	}

	private static Harness harness(int state, String mode, boolean applyResult)
			throws Exception {
		return harness(state, mode, applyResult, false);
	}

	private static Harness harness(int state, String mode, boolean applyResult,
			boolean live) throws Exception {
		BasicPreferenceStore store = new BasicPreferenceStore();
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(store);
		AudioEffectsProfile initial = enabledProfile(0);
		repository.save(initial);
		Promise<Long> position = new DirectPromise<>();
		AtomicReference<PlayableItem> currentSource = new AtomicReference<>();
		PlayableItem item = item("A", live, position);
		currentSource.set(item);
		Engine current = new Engine("current", currentSource, 41, position);
		Engine candidate = new Engine("candidate", currentSource, 42, new DirectPromise<>());
		PlayableItem other = item("B", false, new DirectPromise<>());
		SelectionManager manager = allocate(SelectionManager.class);
		manager.selection = new EngineSelection(candidate, EngineSelection.Ownership.OWNED_NEW,
				EngineSelection.Retirement.RETAIN);
		MediaLib lib = (MediaLib) Proxy.newProxyInstance(MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getMediaEngineManager" -> manager;
					case "getContext" -> RuntimeEnvironment.getApplication();
					case "getPrefs" -> preferenceProxy(MediaLibPrefs.class);
					case "getRecent" -> Proxy.newProxyInstance(BrowsableItem.class.getClassLoader(),
							new Class<?>[]{MediaLib.Recent.class}, (ignored, ignoredMethod, ignoredArgs) ->
									defaultValue(ignoredMethod.getReturnType()));
					default -> defaultValue(method.getReturnType());
				});
		MediaSessionCallback callback = callback(repository, lib, current, item, state);
		List<Backend> backends = new LinkedList<>();
		AudioEffectsController controller = createController(repository, mode, applyResult, backends);
		set(callback, "audioEffectsController", controller);
		assertTrue(controller.bind(current));
		return new Harness(repository, callback, current, candidate, position, other, backends);
	}

	private static MediaSessionCallback callback(AudioEffectsProfileRepository repository,
			MediaLib lib, Engine current, PlayableItem item, int state) throws Exception {
		PlaybackOwnership ownership = new PlaybackOwnership();
		PlaybackOwnership.Token owner = ownership.adopt(Object.class, item, current);
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		MutableAccess access = new MutableAccess(callback, owner.generation(), current);
		set(callback, "listeners", new LinkedList<>());
		set(callback, "lib", lib);
		set(callback, "session", allocate(FakeSession.class));
		set(callback, "service", allocate(FakeService.class));
		set(callback, "handler", new Handler(Looper.getMainLooper()));
		set(callback, "playbackControlPrefs", preferenceProxy());
		PlaybackStateCompat.CustomAction action = new PlaybackStateCompat.CustomAction.Builder(
				"test", "test", android.R.drawable.ic_media_play).build();
		set(callback, "playbackActions", new PlaybackCustomActions(action, action, action, action,
				action, action, action, action));
		set(callback, "playbackOwnership", ownership);
		set(callback, "playbackQueueContext", new PlaybackQueueContext<>(PlayableItemResolver::unwrap));
		set(callback, "playbackEngineLease", new PlaybackEngineLeaseController(ownership, access));
		set(callback, "playbackTransition", new PlaybackTransition());
		set(callback, "playbackLifecycle", new RemotePlaybackLifecycleController(r -> {}));
		set(callback, "preparationStatus", new PlaybackPreparationStatus());
		set(callback, "deferredInitialSeek", new DeferredInitialSeek());
		PlaybackProgressPolicy policy = new PlaybackProgressPolicy(() -> 0L);
		set(callback, "progressPolicy", policy);
		set(callback, "progressCoordinator", new PlaybackProgressCoordinator(callback, policy,
				(task, delay) -> {}, ignored -> completed(0L), ignored -> false, lib));
		set(callback, "playbackAdvanceWatchdog", new PlaybackAdvanceWatchdog(
				(task, delay) -> {}, () -> {}));
		set(callback, "playerTask", completedVoid());
		set(callback, "permanentFocusLoss", new PermanentFocusLoss(
				new PermanentFocusLoss.Scheduler() {
					@Override public void postDelayed(Runnable task, long delayMillis) {}
					@Override public void removeCallbacks(Runnable task) {}
				}, () -> false, () -> {}));
		set(callback, "videoOutput", new VideoOutputCoordinator());
		set(callback, "playbackRequestRevision", owner.generation());
		set(callback, "engine", current);
		set(callback, "playbackSnapshot", new PlaybackSnapshot(1L, item,
				new PlaybackStateCompat.Builder().setState(state, 0L, 1F).build(), null));
		return callback;
	}

	private static AudioEffectsController createController(AudioEffectsProfileRepository repository,
			String modeName, boolean applyResult, List<Backend> backends) throws Exception {
		Class<?> backendType = Class.forName(
				"me.aap.fermata.media.audio.AudioEffectsBackend");
		Class<?> factoryType = Class.forName(
				"me.aap.fermata.media.audio.AudioEffectsController$BackendFactory");
		Class<?> modeType = Class.forName(
				"me.aap.fermata.media.audio.EqualizerUpdateMode");
		Object mode = Enum.valueOf((Class) modeType, modeName);
		Object factory = Proxy.newProxyInstance(factoryType.getClassLoader(),
				new Class<?>[]{factoryType}, (proxy, method, args) -> {
					Backend probe = new Backend(mode, backends.isEmpty() || applyResult);
					backends.add(probe);
					return Proxy.newProxyInstance(backendType.getClassLoader(),
							new Class<?>[]{backendType}, probe);
				});
		var constructor = AudioEffectsController.class.getDeclaredConstructor(
				AudioEffectsProfileRepository.class, factoryType, Runnable.class);
		constructor.setAccessible(true);
		return (AudioEffectsController) constructor.newInstance(repository, factory, (Runnable) () -> {});
	}

	private static PlayableItem item(String name, boolean live, Promise<Long> position) {
		BrowsableItem parent = (BrowsableItem) Proxy.newProxyInstance(
				BrowsableItem.class.getClassLoader(), new Class<?>[]{BrowsableItem.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getPrefs" -> preferenceProxy(BrowsableItemPrefs.class);
					case "getQueue" -> direct(List.of());
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
		PlayableItemPrefs prefs = preferenceProxy(PlayableItemPrefs.class);
		DirectPromise<MediaMetadataCompat> media = new DirectPromise<>();
		media.complete(new MediaMetadataCompat.Builder().putString(
				MediaMetadataCompat.METADATA_KEY_TITLE, name).build());
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getParent" -> parent;
					case "getPrefs" -> prefs;
					case "getMediaData" -> media;
					case "getQueueId" -> direct(0L);
					case "getMediaDescription" -> direct(new MediaDescriptionCompat.Builder()
							.setMediaId(name).setTitle(name).build());
					case "getDuration" -> direct(60_000L);
					case "isLiveStream" -> live;
					case "isStream", "isPlaybackTransportCommand", "isVideo", "isExternal" -> false;
					case "isSeekable" -> !live;
					case "getId", "getName", "toString" -> name;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
	}

	private static AudioEffectsProfile enabledProfile(int preamp) {
		return new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION, true, true,
				AudioEffectsProfile.flatCurveDb(), preamp, false, 0, false, 0, false, 0, 0);
	}

	private static AudioEffectsProfile profileWithCurve(int db) {
		int[] curve = AudioEffectsProfile.flatCurveDb();
		curve[5] = db;
		return new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION, true, true,
				curve, 0, false, 0, false, 0, false, 0, 0);
	}

	@SuppressWarnings("unchecked")
	private static <T> T preferenceProxy(Class<?> type) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
				(proxy, method, args) -> defaultValue(method.getReturnType()));
	}

	private static <T> DirectPromise<T> direct(T value) {
		DirectPromise<T> result = new DirectPromise<>();
		result.complete(value);
		return result;
	}

	private static PlaybackControlPrefs preferenceProxy() {
		return preferenceProxy(PlaybackControlPrefs.class);
	}

	private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
			throws Exception {
		var method = MediaSessionCallback.class.getDeclaredMethod(name, types);
		method.setAccessible(true);
		return method.invoke(target, args);
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
	}

	private static void set(Object target, String name, Object value) throws Exception {
		Field field = MediaSessionCallback.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object get(Object target, String name) throws Exception {
		Field field = MediaSessionCallback.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return '\0';
		return null;
	}

	private static final class Harness {
		private final AudioEffectsProfileRepository repository;
		private final MediaSessionCallback callback;
		private Engine current;
		private Engine candidate;
		private Promise<Long> position;
		private PlayableItem other;
		private final List<Backend> backends;

		private Harness(AudioEffectsProfileRepository repository, MediaSessionCallback callback,
				Engine current, Engine candidate, Promise<Long> position, PlayableItem other,
				List<Backend> backends) {
			this.repository = repository;
			this.callback = callback;
			this.current = current;
			this.candidate = candidate;
			this.position = position;
			this.other = other;
			this.backends = backends;
		}
	}

	private static final class Backend implements java.lang.reflect.InvocationHandler {
		private final Object mode;
		private final boolean applyResult;
		private int applyCount;
		private int releaseCount;
		private AudioEffectsProfile lastProfile;

		private Backend(Object mode, boolean applyResult) {
			this.mode = mode;
			this.applyResult = applyResult;
		}

		@Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
			return switch (method.getName()) {
				case "getCapabilities" -> emptyCapabilities();
				case "getEqualizerUpdateMode" -> mode;
				case "apply" -> {
					applyCount++;
					lastProfile = (AudioEffectsProfile) args[0];
					yield !(Boolean) args[1] || applyResult;
				}
				case "release" -> { releaseCount++; yield null; }
				case "bypass" -> null;
				case "equals" -> proxy == args[0];
				case "hashCode" -> System.identityHashCode(proxy);
				default -> defaultValue(method.getReturnType());
			};
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private static EnumSet emptyCapabilities() {
			try {
				return EnumSet.noneOf((Class) Class.forName(
						"me.aap.fermata.media.audio.AudioEffectCapability"));
			} catch (ClassNotFoundException error) {
				throw new AssertionError(error);
			}
		}
	}

	private static final class DirectPromise<T> extends Promise<T> {
		@Override public FutureSupplier<T> main() { return this; }
	}

	private static final class Engine implements MediaEngine {
		private final String name;
		private final AtomicReference<PlayableItem> source;
		private final int sessionId;
		private final FutureSupplier<Long> position;
		private RuntimeException positionFailure;
		private long lastPosition = -1L;
		private int prepareCount;
		private int startCount;
		private int closeCount;

		private Engine(String name, AtomicReference<PlayableItem> source, int sessionId,
				FutureSupplier<Long> position) {
			this.name = name;
			this.source = source;
			this.sessionId = sessionId;
			this.position = position;
		}

		@Override public void prepare(PlayableItem source) { this.source.set(source); prepareCount++; }
		@Override public int getId() { return sessionId; }
		@Override public void start() { startCount++; }
		@Override public void stop() {}
		@Override public void pause() {}
		@Override public PlayableItem getSource() { return source.get(); }
		@Override public FutureSupplier<Long> getDuration() { return new Promise<>(); }
		@Override public FutureSupplier<Long> getPosition() {
			if (positionFailure != null) throw positionFailure;
			return position;
		}
		@Override public void setPosition(long position) { lastPosition = position; }
		@Override public FutureSupplier<Float> getSpeed() { return completed(1F); }
		@Override public void setSpeed(float speed) {}
		@Override public void setVideoView(@Nullable me.aap.fermata.ui.view.VideoView view) {}
		@Override public float getVideoWidth() { return 0; }
		@Override public float getVideoHeight() { return 0; }
		@Override public int getAudioSessionId() { return sessionId; }
		@Override public void close() { closeCount++; }
		@Override public String toString() { return name; }
	}

	private static final class SelectionManager extends MediaEngineManager {
		private EngineSelection selection;
		private SelectionManager() { super(null); }
		@Override public EngineSelection createEngineSelection(MediaEngine current, PlayableItem item,
				MediaEngine.Listener listener) { return selection; }
	}

	private static final class MutableAccess implements PlaybackEngineLeaseController.Access {
		private final MediaSessionCallback callback;
		private long revision;
		private MutableAccess(MediaSessionCallback callback, long revision, MediaEngine engine) {
			this.callback = callback;
			this.revision = revision;
			try { set(callback, "engine", engine); } catch (Exception error) {
				throw new AssertionError(error);
			}
		}
		@Override public boolean terminal() { return false; }
		@Override public long requestRevision() {
			try { return (long) get(callback, "playbackRequestRevision"); } catch (Exception error) {
				throw new AssertionError(error);
			}
		}
		@Override public void requestRevision(long revision) {
			this.revision = revision;
			try { set(callback, "playbackRequestRevision", revision); } catch (Exception error) {
				throw new AssertionError(error);
			}
		}
		@Override public MediaEngine engineSlot() { return callback.getEngine(); }
		@Override public void engineSlot(MediaEngine engine) {
			try { set(callback, "engine", engine); } catch (Exception error) {
				throw new AssertionError(error);
			}
		}
	}

	private static final class FakeSession extends MediaSessionCompat {
		private FakeSession() { super(null, "test"); }
		@Override public void setPlaybackState(PlaybackStateCompat state) {}
		@Override public void setMetadata(MediaMetadataCompat metadata) {}
		@Override public void setRepeatMode(int repeatMode) {}
		@Override public void setShuffleMode(int shuffleMode) {}
		@Override public void setActive(boolean active) {}
		@Override public void setQueue(List<MediaSessionCompat.QueueItem> queue) {}
	}

	private static final class FakeService extends FermataMediaService {
		private FakeService() {}
		@Override void updateNotification(int state, @Nullable PlayableItem item) {}
		@Override boolean requestPlaybackAudioFocus(MediaEngine engine,
				android.media.AudioManager audioManager,
				androidx.media.AudioFocusRequestCompat request, int state, PlayableItem item) {
			return true;
		}
	}
}
