package me.aap.fermata.media.engine;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Set;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackItem;

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

	private PlayableItem remoteItem(PlaybackRequestProfile profile) {
		return (PlayableItem) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PlayableItem.class, RemotePlaybackItem.class},
				(proxy, method, args) -> {
					if (method.getName().equals("getPlaybackRequestProfile")) return profile;
					if (method.getName().equals("equals")) return proxy == args[0];
					if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
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
}
