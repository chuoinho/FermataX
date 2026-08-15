package me.aap.fermata.engine.vlc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;
import me.aap.fermata.ui.policy.VideoFormatSnapshot;
import me.aap.fermata.ui.policy.VideoRenderPlan;
import me.aap.fermata.ui.policy.VideoViewport;

public class VlcEngineProviderTest {
	@Test
	public void libVlcReceivesMutableDefensiveOptionsCopy() {
		List<String> source = List.of("--network-caching=60000");
		List<String> copy = VlcEngineProvider.mutableOptions(source);

		assertNotSame(source, copy);
		copy.add("--no-stats");
		assertEquals(List.of("--network-caching=60000"), source);
	}

	@Test
	public void doesNotClaimHeadersOrRedirectPolicyItCannotEnforce() {
		var capabilities = VlcEngineProvider.playbackCapabilities();
		assertFalse(capabilities.contains(
				me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REQUEST_HEADERS));
		assertFalse(capabilities.contains(
				me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.AUTHORIZATION_HEADER));
		assertFalse(capabilities.contains(
				me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.REDIRECT_ORIGIN_POLICY));
	}

	@Test
	public void acceptsOnlyTheInternalStremioP2pBridge() {
		VlcEngineProvider provider = new VlcEngineProvider();
		PlaybackRequestProfile safe = PlaybackRequestProfile.builder(
				URI.create("http://127.0.0.1:43210/torrent/token/key"), "p2p")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(EngineCapability.P2P_STREAMING)
				.build();
		var capabilities = provider.capabilitiesFor(safe);

		assertNotNull(capabilities);
		assertTrue(capabilities.contains(EngineCapability.P2P_STREAMING));
		assertTrue(capabilities.contains(EngineCapability.REDIRECT_ORIGIN_POLICY));
		assertNull(provider.capabilitiesFor(PlaybackRequestProfile.builder(
				URI.create("http://127.0.0.1:43210/private-file"), "local")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(EngineCapability.P2P_STREAMING)
				.build()));
		assertNull(provider.capabilitiesFor(PlaybackRequestProfile.builder(
				URI.create("https://example.invalid/torrent/token/key"), "remote")
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
				.requireCapability(EngineCapability.P2P_STREAMING)
				.build()));
	}

	@Test
	public void nativeTransformIsResetForAnyRenderPlanIncludingAnUnmeasuredHandoff() {
		assertTrue(VlcEngine.usesAutomaticNativeTransform(
				new VideoRenderPlan(770, 700, 770, 433, 770, 433, 0, false)));
		assertTrue(VlcEngine.usesAutomaticNativeTransform(
				new VideoRenderPlan(770, 350, 1244, 700, 1244, 700, 1, false)));
		assertTrue(VlcEngine.usesAutomaticNativeTransform(
				new VideoRenderPlan(0, 700, -1, -1, -1, -1, 0, true)));
	}

	@Test
	public void voutWindowUsesDecoderSurfaceInsteadOfHostViewport() {
		assertNull(VlcEngine.voutWindowSize(
				new VideoRenderPlan(0, 700, -1, -1, -1, -1, 0, true)));
		assertEquals(new VideoViewport(2340, 1080), VlcEngine.voutWindowSize(
				new VideoRenderPlan(2340, 1080, 2340, 1080, -1, -1, 0, true)));
		assertEquals(new VideoViewport(770, 700), VlcEngine.voutWindowSize(
				new VideoRenderPlan(770, 700, 770, 700, 770, 700, 0, true)));
		assertEquals(new VideoViewport(788, 433), VlcEngine.voutWindowSize(
				new VideoRenderPlan(770, 700, 770, 433, 788, 433, 0, false)));
	}

	@Test
	public void vlcFormatUsesVisibleFrameAndNormalizesInvalidSampleAspectRatio() {
		VideoFormatSnapshot anamorphic = VlcEngine.videoFormatSnapshot(720, 576, 704, 576,
				16, 11);
		assertEquals(720f, anamorphic.codedWidth(), 0f);
		assertEquals(704f, anamorphic.displayWidth(), 0f);
		assertEquals(16f / 11f, anamorphic.normalizedPixelWidthHeightRatio(), 0f);

		VideoFormatSnapshot invalidSar = VlcEngine.videoFormatSnapshot(1920, 1088, 1920, 1080,
				0, 0);
		assertEquals(1f, invalidSar.normalizedPixelWidthHeightRatio(), 0f);
		assertEquals(1080f, invalidSar.displayHeight(), 0f);
	}

	@Test
	public void layoutStateRejectsDuplicatesAndAcceptsChangedGeometry() {
		VlcEngine.VideoLayoutState state = new VlcEngine.VideoLayoutState();
		assertTrue(state.update(496, 272, 496, 272, 1, 1));
		assertFalse(state.update(496, 272, 496, 272, 1, 1));
		assertTrue(state.update(1920, 1088, 1920, 1080, 1, 1));
	}

	@Test
	public void zeroLayoutCallbackIsRejectedBeforeItCanClearKnownFormat() {
		VlcEngine.VideoLayoutState state = new VlcEngine.VideoLayoutState();
		assertTrue(state.update(1920, 1088, 1920, 1080, 1, 1));
		assertFalse(VlcEngine.VideoLayoutState.isValid(0, 0, 0, 0));
		assertFalse(state.update(0, 0, 0, 0, 0, 0));

		VideoFormatSnapshot snapshot = state.snapshot();
		assertEquals(1920f, snapshot.codedWidth(), 0f);
		assertEquals(1088f, snapshot.codedHeight(), 0f);
		assertEquals(1920f, snapshot.visibleWidth(), 0f);
		assertEquals(1080f, snapshot.visibleHeight(), 0f);
	}

	@Test
	public void resettingWindowMemoReappliesIdenticalDimensionsAfterViewHandoff()
			throws ReflectiveOperationException {
		VlcEngine engine = allocateWithoutConstructor(VlcEngine.class);
		assertTrue(engine.rememberWindowSize(1968, 1080));
		assertFalse(engine.rememberWindowSize(1968, 1080));

		engine.resetWindowSizeMemo();

		assertTrue(engine.rememberWindowSize(1968, 1080));
	}

	private static <T> T allocateWithoutConstructor(Class<T> type)
			throws ReflectiveOperationException {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		var field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return type.cast(unsafeType.getMethod("allocateInstance", Class.class)
				.invoke(unsafe, type));
	}
}
