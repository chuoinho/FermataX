package me.aap.fermata.addon.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.external.ExternalPlaybackRequest;
import me.aap.fermata.addon.external.ExternalPlaybackTargetKind;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.ExtRoot;

public class WebExternalPlaybackItemTest {
	@Test
	public void preservesExactMetadataAndWebRoute() {
		AtomicBoolean engineRequested = new AtomicBoolean();
		MediaEngine expectedEngine = (MediaEngine) Proxy.newProxyInstance(
				MediaEngine.class.getClassLoader(), new Class<?>[]{MediaEngine.class},
				(proxy, method, args) -> null);
		ExternalPlaybackRequest request = new ExternalPlaybackRequest("movie:exact", "Exact title",
				"https://images.example/exact.jpg", 123_456L,
				ExternalPlaybackTargetKind.EXTERNAL_HTTP,
				"https://provider.example/watch/exact", uri -> {});
		WebExternalPlaybackItem item = new WebExternalPlaybackItem(null, request,
				(current, listener, route) -> {
					engineRequested.set(true);
					assertSame(request, route);
					return expectedEngine;
				});

		assertEquals("web:external:movie:exact", item.getId());
		assertEquals("Exact title", item.getName());
		assertEquals("Exact title", item.getExternalPlaybackRequest().getTitle());
		assertEquals("https://images.example/exact.jpg",
				item.getExternalPlaybackRequest().getArtworkUri());
		assertEquals(123_456L, item.getExternalPlaybackRequest().getDurationMillis());
		assertEquals(AddonCapability.WEB, ((ExtRoot) item.getRoot()).getRouteCapability());
		assertSame(expectedEngine, item.getMediaEngine(null, MediaEngine.Listener.DUMMY));
		assertEquals(true, engineRequested.get());
	}

	@Test
	public void webEngineRetainsTheStremioWrapperAsMediaSessionSource() {
		ExternalPlaybackRequest request = new ExternalPlaybackRequest("movie:exact", "Exact title",
				"", 0L, ExternalPlaybackTargetKind.EXTERNAL_HTTP,
				"https://provider.example/watch/exact", uri -> {});
		WebExternalMediaEngine engine = new WebExternalMediaEngine(
				new WebExternalMediaEngine.Host() {
					@Override
					public void attachExternalPlayback(WebExternalMediaEngine engine) {
					}

					@Override
					public void detachExternalPlayback(WebExternalMediaEngine engine) {
					}
				}, request, MediaEngine.Listener.DUMMY);
		PlayableItem wrapper = (PlayableItem) Proxy.newProxyInstance(
				PlayableItem.class.getClassLoader(), new Class<?>[]{PlayableItem.class},
				(proxy, method, args) -> null);
		engine.prepare(wrapper);
		assertSame(wrapper, engine.getSource());
		assertNotNull(engine.getRequest().getNavigationPolicy());
	}
}
