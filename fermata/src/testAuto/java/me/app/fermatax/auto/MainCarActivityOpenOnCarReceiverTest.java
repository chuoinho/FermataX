package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static me.aap.utils.async.Completed.completed;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;
import me.aap.fermata.auto.OpenOnCarKind;
import me.aap.fermata.auto.OpenOnCarRequest;
import me.aap.fermata.auto.OpenOnCarToken;

public class MainCarActivityOpenOnCarReceiverTest {
	@Test public void typedMediaUsesOnlyGuardedMediaContinuation() {
		var item = (me.aap.fermata.media.lib.MediaLib.PlayableItem) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class[]{me.aap.fermata.media.lib.MediaLib.PlayableItem.class},
				(proxy, method, args) -> null);
		OpenOnCarRequest request = new OpenOnCarRequest(OpenOnCarKind.MEDIA_ITEM, 0,
				new me.aap.fermata.auto.OpenOnCarMediaRouting.MediaItem(item, 1234L),
				new OpenOnCarToken(1, 1, 1, 1, 1));
		assertEquals(OpenResult.LOAD_DISPATCHED, MainCarActivity.dispatchPhoneRequest(request, () -> true,
				id -> { throw new AssertionError(); }, web -> { throw new AssertionError(); }, media -> {
					org.junit.Assert.assertSame(request, media); return completed(OpenResult.LOAD_DISPATCHED);
				}).peek());
		assertEquals(OpenResult.CANCELLED, MainCarActivity.dispatchPhoneRequest(request, () -> false,
				id -> { throw new AssertionError(); }, web -> { throw new AssertionError(); },
				media -> { throw new AssertionError(); }).peek());
	}
	@Test public void exactWebRequestUsesTypedContinuationNotSetInput() {
		AtomicInteger addons = new AtomicInteger(), webLoads = new AtomicInteger();
		OpenOnCarRequest request = new OpenOnCarRequest(OpenOnCarKind.WEB_URL,
				me.aap.fermata.R.id.web_browser_fragment, "https://example.test",
				new OpenOnCarToken(1, 1, 1, 1, 1));
		OpenResult result = MainCarActivity.dispatchPhoneRequest(request, () -> true,
				id -> { addons.incrementAndGet(); return completed(OpenResult.OPENED); },
				typed -> {
					org.junit.Assert.assertSame(request, typed);
					webLoads.incrementAndGet(); return completed(OpenResult.LOAD_DISPATCHED);
				}).peek();
		assertEquals(OpenResult.LOAD_DISPATCHED, result);
		assertEquals(0, addons.get());
		assertEquals(1, webLoads.get());
	}
	@Test public void staleTypedRequestDoesNotRunAnyContinuation() {
		OpenOnCarRequest request = new OpenOnCarRequest(OpenOnCarKind.WEB_URL,
				me.aap.fermata.R.id.web_browser_fragment, "https://example.test",
				new OpenOnCarToken(1, 1, 1, 1, 1));
		assertEquals(OpenResult.CANCELLED, MainCarActivity.dispatchPhoneRequest(request,
				() -> false, id -> { throw new AssertionError(); },
				typed -> { throw new AssertionError(); }).peek());
	}
	@Test
	public void webUrlStopsAtReceiverBeforeAddonContinuation() {
		AtomicInteger addonContinuations = new AtomicInteger();
		OpenOnCarRequest request = new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 71,
				"https://example.test", new OpenOnCarToken(1, 1, 1, 1, 1));

		OpenResult result = MainCarActivity.dispatchPhoneRequest(request, () -> true, id -> {
			addonContinuations.incrementAndGet();
			return completed(OpenResult.OPENED);
		}).peek();

		assertEquals(OpenResult.NOT_READY, result);
		assertEquals(0, addonContinuations.get());
	}
	@Test
	public void stremioPlayerUsesItsDedicatedTypedContinuation() {
		AtomicInteger addons = new AtomicInteger(), web = new AtomicInteger(), stremio = new AtomicInteger();
		OpenOnCarRequest request = new OpenOnCarRequest(OpenOnCarKind.STREMIO_PLAYER,
				me.aap.fermata.R.id.stremio_fragment, "https://web.stremio.com/#/player/fixture",
				new OpenOnCarToken(1, 1, 1, 1, 1));

		OpenResult result = MainCarActivity.dispatchPhoneRequest(request, () -> true,
				id -> { addons.incrementAndGet(); return completed(OpenResult.OPENED); },
				typed -> { web.incrementAndGet(); return completed(OpenResult.LOAD_DISPATCHED); },
				typed -> { throw new AssertionError(); },
				typed -> {
					org.junit.Assert.assertSame(request, typed);
					stremio.incrementAndGet();
					return completed(OpenResult.LOAD_DISPATCHED);
				}).peek();

		assertEquals(OpenResult.LOAD_DISPATCHED, result);
		assertEquals(0, addons.get());
		assertEquals(0, web.get());
		assertEquals(1, stremio.get());
	}
}
