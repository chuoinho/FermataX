package me.aap.fermata.media.engine;

import static me.aap.fermata.media.engine.EngineSelection.Ownership.BORROWED;
import static me.aap.fermata.media.engine.EngineSelection.Ownership.NO_CANDIDATE;
import static me.aap.fermata.media.engine.EngineSelection.Ownership.OWNED_NEW;
import static me.aap.fermata.media.engine.EngineSelection.Ownership.PREEXISTING;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_EXO;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_VLC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;

public class MediaEngineManagerTest {
	@Test
	public void providerFailureDoesNotEscapeEngineFactory() {
		MediaEngineProvider failing = new MediaEngineProvider() {
			@Override
			public void init(Context context) {
			}

			@Override
			public MediaEngine createEngine(MediaEngine.Listener listener) {
				throw new UnsupportedOperationException("provider failed");
			}
		};

		assertNull(MediaEngineManager.createSafely(failing, null, false));
		assertNull(MediaEngineManager.createSafely(failing, null, true));
	}

	@Test
	public void unsupportedPreferredProviderFallsBackWithoutAffectingRegularItems() {
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(
				URI.create("https://media.invalid/video.m3u8"), "source").build();
		PlayableItem remote = (PlayableItem) Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class<?>[]{PlayableItem.class, RemotePlaybackItem.class},
				(proxy, method, args) -> {
					if (method.getName().equals("getPlaybackRequestProfile")) return profile;
					throw new AssertionError("Unexpected method: " + method.getName());
				});
		MediaEngineProvider unsupported = provider(Set.of());
		MediaEngineProvider supported = provider(Set.of(
				PlaybackRequestProfile.EngineCapability.REDIRECT_ORIGIN_POLICY));

		assertSame(supported, MediaEngineManager.firstSupporting(
				remote, unsupported, supported));
		assertNull(MediaEngineManager.firstSupporting(remote, unsupported));
	}

	@Test
	public void detectsP2pBeforeSelectingThePlaybackEngine() {
		PlaybackRequestProfile p2p = PlaybackRequestProfile.builder(
				URI.create("http://127.0.0.1/stremio-pending/opaque"), "p2p")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(PlaybackRequestProfile.EngineCapability.P2P_STREAMING)
				.build();
		PlaybackRequestProfile direct = PlaybackRequestProfile.builder(
				URI.create("https://media.invalid/video.mp4"), "direct").build();

		assertTrue(MediaEngineManager.requiresP2p(remoteItem(p2p)));
		assertFalse(MediaEngineManager.requiresP2p(remoteItem(direct)));
	}

	@Test
	public void differentP2pItemRequiresFreshEngineButSameItemCanReuseIt() {
		PlaybackRequestProfile p2p = PlaybackRequestProfile.builder(
				URI.create("http://127.0.0.1/stremio-pending/opaque"), "p2p")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(PlaybackRequestProfile.EngineCapability.P2P_STREAMING)
				.build();
		PlayableItem first = remoteItem(p2p);
		PlayableItem second = remoteItem(p2p);

		assertFalse(MediaEngineManager.requiresFreshP2pEngine(engine(first), first));
		assertTrue(MediaEngineManager.requiresFreshP2pEngine(engine(first), second));
		assertTrue(MediaEngineManager.requiresFreshP2pEngine(engine(null), second));
	}

	@Test
	public void itemSuppliedCandidatesArePreexistingOrBorrowed() {
		TestMediaPlayerProvider mediaPlayer = new TestMediaPlayerProvider();
		MediaEngineManager manager = manager(mediaPlayer);
		TestEngine current = new TestEngine(MEDIA_ENG_MP, null);

		EngineSelection reused = manager.createEngineSelection(current.engine,
				item(current.engine, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY);
		assertSelection(reused, current.engine, PREEXISTING);
		assertEquals(0, current.closeCount);
		assertEquals(0, mediaPlayer.createCount);

		TestEngine supplied = new TestEngine(MEDIA_ENG_EXO, null);
		EngineSelection borrowed = manager.createEngineSelection(current.engine,
				item(supplied.engine, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY);
		assertSelection(borrowed, supplied.engine, BORROWED);
		assertEquals(1, current.closeCount);
		assertEquals(0, mediaPlayer.createCount);

		TestEngine withoutCurrent = new TestEngine(MEDIA_ENG_EXO, null);
		EngineSelection newBorrowed = manager.createEngineSelection(null,
				item(withoutCurrent.engine, false, true, false, MEDIA_ENG_MP),
				MediaEngine.Listener.DUMMY);
		assertSelection(newBorrowed, withoutCurrent.engine, BORROWED);
	}

	@Test
	public void castLikeCachedCustomProviderCandidatesAreConservativelyBorrowed() {
		TestMediaPlayerProvider mediaPlayer = new TestMediaPlayerProvider();
		MediaEngineManager manager = manager(mediaPlayer);
		TestEngine current = new TestEngine(MEDIA_ENG_MP, null);
		TestEngine cached = new TestEngine(MEDIA_ENG_EXO, null);
		TestProvider custom = new TestProvider(true, () -> cached.engine);
		manager.setCustomEngineProvider(custom);

		EngineSelection borrowed = manager.createEngineSelection(current.engine,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY);
		assertSelection(borrowed, cached.engine, BORROWED);
		assertEquals(0, current.closeCount);

		EngineSelection reused = manager.createEngineSelection(cached.engine,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY);
		assertSelection(reused, cached.engine, PREEXISTING);
		assertEquals(0, cached.closeCount);

		custom.factory = () -> null;
		assertSelection(manager.createEngineSelection(current.engine,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				null, NO_CANDIDATE);
		assertEquals(0, current.closeCount);

		custom.failure = new IllegalStateException("failed");
		assertSelection(manager.createEngineSelection(current.engine,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				null, NO_CANDIDATE);
		assertEquals(0, current.closeCount);
	}

	@Test
	public void unsupportedCustomProviderFallsThroughToBuiltInSelection() {
		TestEngine created = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider mediaPlayer = new TestMediaPlayerProvider(true, () -> created.engine);
		MediaEngineManager manager = manager(mediaPlayer);
		TestProvider custom = new TestProvider(false, () -> {
			throw new AssertionError("Unsupported custom provider must not be called");
		});
		manager.setCustomEngineProvider(custom);

		EngineSelection selection = manager.createEngineSelection(null,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY);
		assertSelection(selection, created.engine, OWNED_NEW);
		assertEquals(0, custom.createCount);
		assertEquals(1, mediaPlayer.createCount);
	}

	@Test
	public void noAdditionalPlayerBranchesPreserveCloseReuseAndCreation() {
		TestMediaPlayerProvider unsupported = new TestMediaPlayerProvider(false, () -> null);
		MediaEngineManager unsupportedManager = manager(unsupported);
		TestEngine unsupportedCurrent = new TestEngine(MEDIA_ENG_EXO, null);
		assertSelection(unsupportedManager.createEngineSelection(unsupportedCurrent.engine,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				null, NO_CANDIDATE);
		assertEquals(1, unsupportedCurrent.closeCount);

		TestMediaPlayerProvider reusableProvider = new TestMediaPlayerProvider();
		MediaEngineManager reusableManager = manager(reusableProvider);
		TestEngine reusable = new TestEngine(MEDIA_ENG_MP, null);
		assertSelection(reusableManager.createEngineSelection(reusable.engine,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				reusable.engine, PREEXISTING);
		assertEquals(0, reusable.closeCount);
		assertEquals(0, reusableProvider.createCount);

		TestEngine replacement = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider replacingProvider =
				new TestMediaPlayerProvider(true, () -> replacement.engine);
		MediaEngineManager replacingManager = manager(replacingProvider);
		TestEngine incompatible = new TestEngine(MEDIA_ENG_EXO, null);
		assertSelection(replacingManager.createEngineSelection(incompatible.engine,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				replacement.engine, OWNED_NEW);
		assertEquals(1, incompatible.closeCount);

		TestEngine first = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider firstProvider = new TestMediaPlayerProvider(true, () -> first.engine);
		assertSelection(manager(firstProvider).createEngineSelection(null,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				first.engine, OWNED_NEW);
	}

	@Test
	public void additionalPlayerBranchesClassifyNullReuseReplacementAndFallback() {
		TestMediaPlayerProvider unsupportedMediaPlayer = new TestMediaPlayerProvider(false, () -> null);
		MediaEngineManager noProvider = manager(unsupportedMediaPlayer);
		noProvider.exoPlayer = new TestProvider(false, () -> null);
		TestEngine unsupportedCurrent = new TestEngine(MEDIA_ENG_EXO, null);
		assertSelection(noProvider.createEngineSelection(unsupportedCurrent.engine,
				item(null, false, true, false, MEDIA_ENG_EXO), MediaEngine.Listener.DUMMY),
				null, NO_CANDIDATE);
		assertEquals(1, unsupportedCurrent.closeCount);

		TestProvider reusableProvider = new TestProvider(true, () -> {
			throw new AssertionError("Reusable engine must not invoke its provider");
		});
		MediaEngineManager reusableManager = manager(new TestMediaPlayerProvider());
		reusableManager.exoPlayer = reusableProvider;
		TestEngine reusable = new TestEngine(MEDIA_ENG_EXO, null);
		assertSelection(reusableManager.createEngineSelection(reusable.engine,
				item(null, false, true, false, MEDIA_ENG_EXO), MediaEngine.Listener.DUMMY),
				reusable.engine, PREEXISTING);
		assertEquals(0, reusable.closeCount);
		assertEquals(0, reusableProvider.createCount);

		TestEngine replacement = new TestEngine(MEDIA_ENG_EXO, null);
		TestProvider replacingProvider = new TestProvider(true, () -> replacement.engine);
		MediaEngineManager replacingManager = manager(new TestMediaPlayerProvider());
		replacingManager.exoPlayer = replacingProvider;
		TestEngine incompatible = new TestEngine(MEDIA_ENG_VLC, null);
		assertSelection(replacingManager.createEngineSelection(incompatible.engine,
				item(null, false, true, false, MEDIA_ENG_EXO), MediaEngine.Listener.DUMMY),
				replacement.engine, OWNED_NEW);
		assertEquals(1, incompatible.closeCount);

		TestEngine first = new TestEngine(MEDIA_ENG_EXO, null);
		TestProvider firstProvider = new TestProvider(true, () -> first.engine);
		MediaEngineManager firstManager = manager(new TestMediaPlayerProvider());
		firstManager.exoPlayer = firstProvider;
		assertSelection(firstManager.createEngineSelection(null,
				item(null, false, true, false, MEDIA_ENG_EXO), MediaEngine.Listener.DUMMY),
				first.engine, OWNED_NEW);

		TestProvider preferred = new TestProvider(false, () -> {
			throw new AssertionError("Unsupported preferred provider must not be called");
		});
		TestEngine fallbackEngine = new TestEngine(MEDIA_ENG_VLC, null);
		TestProvider fallback = new TestProvider(true, () -> fallbackEngine.engine);
		MediaEngineManager fallbackManager = manager(new TestMediaPlayerProvider(false, () -> null));
		fallbackManager.exoPlayer = preferred;
		fallbackManager.vlcPlayer = fallback;
		assertSelection(fallbackManager.createEngineSelection(null,
				item(null, false, true, false, MEDIA_ENG_EXO), MediaEngine.Listener.DUMMY),
				fallbackEngine.engine, OWNED_NEW);
		assertEquals(0, preferred.createCount);
		assertEquals(1, fallback.createCount);
	}

	@Test
	public void p2pForcesVlcAndRejectsReuseForADifferentSource() {
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(
				URI.create("http://127.0.0.1/stremio-pending/opaque"), "p2p")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(PlaybackRequestProfile.EngineCapability.P2P_STREAMING)
				.build();
		PlayableItem previous = remoteItem(profile);
		PlayableItem target = remoteItem(profile);
		TestEngine selected = new TestEngine(MEDIA_ENG_VLC, null);
		TestProvider vlc = new TestProvider(true, () -> selected.engine);
		TestProvider exo = new TestProvider(true, () -> {
			throw new AssertionError("P2P selection must force VLC");
		});
		MediaEngineManager manager = manager(new TestMediaPlayerProvider());
		manager.exoPlayer = exo;
		manager.vlcPlayer = vlc;
		TestEngine current = new TestEngine(MEDIA_ENG_VLC, previous);

		EngineSelection selection = manager.createEngineSelection(
				current.engine, target, MediaEngine.Listener.DUMMY);
		assertSelection(selection, selected.engine, OWNED_NEW);
		assertEquals(1, current.closeCount);
		assertEquals(0, exo.createCount);
		assertEquals(1, vlc.createCount);
	}

	@Test
	public void builtInHelperClassifiesEveryReuseAndConversionBranch() {
		PlayableItem stream = item(null, true, true, false, MEDIA_ENG_MP);
		PlayableItem regular = item(null, false, true, false, MEDIA_ENG_MP);

		TestMediaPlayerProvider unsupported = new TestMediaPlayerProvider(true, () -> null);
		unsupported.rejectAfterFirstSupportCheck = true;
		TestEngine untouched = new TestEngine(MEDIA_ENG_MP, null);
		assertSelection(manager(unsupported).createEngineSelection(
				untouched.engine, regular, MediaEngine.Listener.DUMMY),
				null, NO_CANDIDATE);
		assertEquals(0, untouched.closeCount);

		TestEngine existingStreamDelegate = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider existingStreamProvider =
				new TestMediaPlayerProvider(true, () -> existingStreamDelegate.engine);
		StreamEngine existingStream =
				new StreamEngine(existingStreamProvider, MediaEngine.Listener.DUMMY);
		assertSelection(manager(existingStreamProvider).createEngineSelection(
				existingStream, stream, MediaEngine.Listener.DUMMY),
				existingStream, PREEXISTING);
		assertEquals(1, existingStreamProvider.createCount);

		TestEngine rawStreamCurrent = new TestEngine(MEDIA_ENG_MP, null);
		TestEngine rawStreamDelegate = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider rawStreamProvider =
				new TestMediaPlayerProvider(true, () -> rawStreamDelegate.engine);
		EngineSelection wrapped = manager(rawStreamProvider).createEngineSelection(
				rawStreamCurrent.engine, stream, MediaEngine.Listener.DUMMY);
		assertTrue(wrapped.candidate() instanceof StreamEngine);
		assertEquals(OWNED_NEW, wrapped.ownership());
		assertEquals(1, rawStreamCurrent.closeCount);

		TestEngine convertedDelegate = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider convertedStreamProvider =
				new TestMediaPlayerProvider(true, () -> convertedDelegate.engine);
		StreamEngine convertedStream =
				new StreamEngine(convertedStreamProvider, MediaEngine.Listener.DUMMY);
		TestEngine convertedRaw = new TestEngine(MEDIA_ENG_MP, null);
		convertedStreamProvider.factory = () -> convertedRaw.engine;
		assertSelection(manager(convertedStreamProvider).createEngineSelection(
				convertedStream, regular, MediaEngine.Listener.DUMMY),
				convertedRaw.engine, OWNED_NEW);
		assertEquals(1, convertedDelegate.closeCount);

		TestEngine rawRegular = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider unused = new TestMediaPlayerProvider(true, () -> {
			throw new AssertionError("Raw regular engine must be reused");
		});
		assertSelection(manager(unused).createEngineSelection(
				rawRegular.engine, regular, MediaEngine.Listener.DUMMY),
				rawRegular.engine, PREEXISTING);
		assertEquals(0, unused.createCount);

		TestEngine newStreamDelegate = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider newStreamProvider =
				new TestMediaPlayerProvider(true, () -> newStreamDelegate.engine);
		EngineSelection newStream = manager(newStreamProvider).createEngineSelection(
				null, stream, MediaEngine.Listener.DUMMY);
		assertTrue(newStream.candidate() instanceof StreamEngine);
		assertEquals(OWNED_NEW, newStream.ownership());

		TestEngine newRaw = new TestEngine(MEDIA_ENG_MP, null);
		TestMediaPlayerProvider newRawProvider =
				new TestMediaPlayerProvider(true, () -> newRaw.engine);
		assertSelection(manager(newRawProvider).createEngineSelection(
				null, regular, MediaEngine.Listener.DUMMY),
				newRaw.engine, OWNED_NEW);
	}

	@Test
	public void builtInHelperClassifiesFactoryFailuresAsNoCandidate() {
		TestMediaPlayerProvider failing = new TestMediaPlayerProvider(true, () -> null);
		failing.failure = new IllegalStateException("failed");
		MediaEngineManager manager = manager(failing);

		assertSelection(manager.createEngineSelection(null,
				item(null, false, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				null, NO_CANDIDATE);
		assertSelection(manager.createEngineSelection(null,
				item(null, true, true, false, MEDIA_ENG_MP), MediaEngine.Listener.DUMMY),
				null, NO_CANDIDATE);
	}

	@Test
	public void legacyCreateEngineStillUsesItsOriginalCloseSemantics() {
		TestMediaPlayerProvider mediaPlayer = new TestMediaPlayerProvider();
		MediaEngineManager manager = manager(mediaPlayer);
		TestEngine current = new TestEngine(MEDIA_ENG_MP, null);
		TestEngine supplied = new TestEngine(MEDIA_ENG_EXO, null);

		assertSame(supplied.engine, manager.createEngine(current.engine,
				item(supplied.engine, false, true, false, MEDIA_ENG_MP),
				MediaEngine.Listener.DUMMY));
		assertEquals(1, current.closeCount);
		assertEquals(0, mediaPlayer.createCount);
	}

	private PlayableItem remoteItem(PlaybackRequestProfile profile) {
		PlayableItemPrefs prefs = itemPrefs(MEDIA_ENG_EXO);
		return (PlayableItem) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PlayableItem.class, RemotePlaybackItem.class},
				(proxy, method, args) -> {
					if (method.getName().equals("getPlaybackRequestProfile")) return profile;
					if (method.getName().equals("getMediaEngine")) return null;
					if (method.getName().equals("getPrefs")) return prefs;
					if (method.getName().equals("isStream")) return false;
					if (method.getName().equals("isSeekable")) return true;
					if (method.getName().equals("isVideo")) return false;
					if (method.getName().equals("equals")) return proxy == args[0];
					if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
					if (method.getName().equals("toString")) return "RemoteTestPlayableItem";
					throw new AssertionError("Unexpected method: " + method.getName());
				});
	}

	private MediaEngine engine(PlayableItem source) {
		return (MediaEngine) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> {
					if (method.getName().equals("getSource")) return source;
					throw new AssertionError("Unexpected method: " + method.getName());
				});
	}

	private static MediaEngineProvider provider(
			Set<PlaybackRequestProfile.EngineCapability> capabilities) {
		return new MediaEngineProvider() {
			@Override
			public void init(Context context) {
			}

			@Override
			public MediaEngine createEngine(MediaEngine.Listener listener) {
				return null;
			}

			@Override
			public Set<PlaybackRequestProfile.EngineCapability> getPlaybackCapabilities() {
				return capabilities;
			}
		};
	}

	private static MediaEngineManager manager(TestMediaPlayerProvider mediaPlayer) {
		MediaLibPrefs prefs = (MediaLibPrefs) Proxy.newProxyInstance(
				MediaLibPrefs.class.getClassLoader(), new Class<?>[]{MediaLibPrefs.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "hasPref" -> true;
					case "getExoEnabledPref", "getVlcEnabledPref" -> false;
					default -> defaultValue(method.getReturnType());
				});
		MediaLib lib = (MediaLib) Proxy.newProxyInstance(MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getPrefs" -> prefs;
					case "getContext" -> null;
					default -> defaultValue(method.getReturnType());
				});
		MediaEngineManager manager = new MediaEngineManager(lib);
		try {
			Field field = MediaEngineManager.class.getDeclaredField("mediaPlayer");
			field.setAccessible(true);
			field.set(manager, mediaPlayer);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError(error);
		}
		manager.exoPlayer = null;
		manager.vlcPlayer = null;
		return manager;
	}

	private static PlayableItem item(MediaEngine suppliedEngine, boolean stream, boolean seekable,
			boolean video, int preferredEngine) {
		PlayableItemPrefs prefs = itemPrefs(preferredEngine);
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getMediaEngine" -> suppliedEngine;
					case "getPrefs" -> prefs;
					case "isStream" -> stream;
					case "isSeekable" -> seekable;
					case "isVideo" -> video;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> "TestPlayableItem";
					default -> defaultValue(method.getReturnType());
				});
	}

	private static PlayableItemPrefs itemPrefs(int preferredEngine) {
		return (PlayableItemPrefs) Proxy.newProxyInstance(PlayableItemPrefs.class.getClassLoader(),
				new Class<?>[]{PlayableItemPrefs.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getAudioEnginePref", "getVideoEnginePref" -> preferredEngine;
					default -> defaultValue(method.getReturnType());
				});
	}

	private static void assertSelection(EngineSelection selection, MediaEngine candidate,
			EngineSelection.Ownership ownership) {
		assertSame(candidate, selection.candidate());
		assertEquals(ownership, selection.ownership());
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

	private static final class TestEngine {
		final MediaEngine engine;
		final int id;
		final PlayableItem source;
		int closeCount;

		TestEngine(int id, PlayableItem source) {
			this.id = id;
			this.source = source;
			engine = (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
					new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
						case "getId" -> this.id;
						case "getSource" -> this.source;
						case "close" -> {
							closeCount++;
							yield null;
						}
						case "equals" -> proxy == args[0];
						case "hashCode" -> System.identityHashCode(proxy);
						case "toString" -> "TestEngine(" + this.id + ")";
						default -> defaultValue(method.getReturnType());
					});
		}
	}

	private static class TestProvider implements MediaEngineProvider {
		boolean supported;
		Supplier<MediaEngine> factory;
		RuntimeException failure;
		int createCount;

		TestProvider(boolean supported, Supplier<MediaEngine> factory) {
			this.supported = supported;
			this.factory = factory;
		}

		@Override
		public void init(Context context) {
		}

		@Override
		public MediaEngine createEngine(MediaEngine.Listener listener) {
			createCount++;
			if (failure != null) throw failure;
			return factory.get();
		}

		@Override
		public boolean supportsPlayback(PlayableItem item) {
			return supported;
		}
	}

	private static final class TestMediaPlayerProvider extends MediaPlayerEngineProvider {
		boolean supported;
		Supplier<MediaEngine> factory;
		RuntimeException failure;
		boolean rejectAfterFirstSupportCheck;
		int supportChecks;
		int createCount;

		TestMediaPlayerProvider() {
			this(true, () -> null);
		}

		TestMediaPlayerProvider(boolean supported, Supplier<MediaEngine> factory) {
			this.supported = supported;
			this.factory = factory;
		}

		@Override
		public void init(Context context) {
		}

		@Override
		public MediaEngine createEngine(MediaEngine.Listener listener) {
			createCount++;
			if (failure != null) throw failure;
			return factory.get();
		}

		@Override
		public boolean supportsPlayback(PlayableItem item) {
			supportChecks++;
			return supported && (!rejectAfterFirstSupportCheck || (supportChecks == 1));
		}
	}
}
