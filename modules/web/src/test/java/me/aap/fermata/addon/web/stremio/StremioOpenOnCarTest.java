package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import me.aap.fermata.media.service.ControlOnlySessionState;

/** Synthetic route/lifecycle fixtures, not evidence that a real Stremio player is portable. */
public class StremioOpenOnCarTest {
	private static final String DETAIL = "https://web.stremio.com/#/detail/series/tt-fixture";
	private static final String PLAYER = "https://web.stremio.com/#/player/" +
			"%7B%22url%22%3A%22https%3A%2F%2Fmedia.invalid%2Ffixture.m3u8%22%7D/" +
			"series/tt-fixture/tt-fixture%3A1%3A2";

	@Test
	public void detailSeasonAndEpisodeBrowsingAreNotPlayerSelections() {
		for (String route : new String[] {DETAIL, DETAIL + "/1", DETAIL + "/1/2"}) {
			assertFalse(StremioWebSessionPolicy.isPlayerRoute(route));
			assertTrue(StremioWebSessionPolicy.isPersistableRoute(route));
		}
		assertTrue(StremioWebSessionPolicy.isPlayerRoute(PLAYER));
		assertFalse(StremioWebSessionPolicy.isPersistableRoute(PLAYER));
		assertEquals(DETAIL, StremioWebSessionPolicy.replaceLegacyPlayerRoute(PLAYER, DETAIL));
	}

	@Test
	public void transferablePlayerRouteRequiresTheExactHostedPlayerShape() {
		assertTrue(StremioOpenOnCarPolicy.acceptsPlayerRoute(PLAYER));
		assertFalse(StremioOpenOnCarPolicy.acceptsPlayerRoute(DETAIL));
		assertFalse(StremioOpenOnCarPolicy.acceptsPlayerRoute(
				"https://web.stremio.com/#/player"));
		assertFalse(StremioOpenOnCarPolicy.acceptsPlayerRoute(
				"https://web.stremio.com:444/#/player/fixture"));
		assertFalse(StremioOpenOnCarPolicy.acceptsPlayerRoute(
				"https://user@web.stremio.com/#/player/fixture"));
		assertFalse(StremioOpenOnCarPolicy.acceptsPlayerRoute(
				"https://web.stremio.example/#/player/fixture"));
	}

	@Test
	public void captureRequiresCurrentForegroundPhoneSourceAndEnabledMode() {
		Object web = new Object();
		assertTrue(StremioOpenOnCarPolicy.isCurrentPhoneSource(true, true, true, true,
				web, web, 9L, 9L));
		assertFalse(StremioOpenOnCarPolicy.isCurrentPhoneSource(false, true, true, true,
				web, web, 9L, 9L));
		assertFalse(StremioOpenOnCarPolicy.isCurrentPhoneSource(true, false, true, true,
				web, web, 9L, 9L));
		assertFalse(StremioOpenOnCarPolicy.isCurrentPhoneSource(true, true, false, true,
				web, web, 9L, 9L));
		assertFalse(StremioOpenOnCarPolicy.isCurrentPhoneSource(true, true, true, false,
				web, web, 9L, 9L));
		assertFalse(StremioOpenOnCarPolicy.isCurrentPhoneSource(true, true, true, true,
				web, new Object(), 9L, 9L));
		assertFalse(StremioOpenOnCarPolicy.isCurrentPhoneSource(true, true, true, true,
				web, web, 9L, 10L));
	}

	@Test
	public void fullMainFramePlayerNavigationIsEligibleForPhoneInterception() {
		assertTrue(StremioOpenOnCarPolicy.shouldInterceptMainFrameRoute(
				PLAYER, true, true, true, true));
		assertFalse(StremioOpenOnCarPolicy.shouldInterceptMainFrameRoute(
				PLAYER, false, true, true, true));
		assertFalse(StremioOpenOnCarPolicy.shouldInterceptMainFrameRoute(
				PLAYER, true, false, true, true));
		assertFalse(StremioOpenOnCarPolicy.shouldInterceptMainFrameRoute(
				PLAYER, true, true, false, true));
		assertFalse(StremioOpenOnCarPolicy.shouldInterceptMainFrameRoute(
				PLAYER, true, true, true, false));
	}

	@Test
	public void playerRouteLimitIs64KiBUtf8NotJavaCharacterCount() {
		String prefix = "https://web.stremio.com/#/player/";
		String boundary = prefix + "x".repeat(65536 - prefix.length());
		assertTrue(StremioWebSessionPolicy.isPlayerRoute(boundary));
		assertFalse(StremioWebSessionPolicy.isPlayerRoute(boundary + "x"));
		String multibyte = prefix + "é".repeat(32753);
		assertTrue(multibyte.length() < 65536);
		assertTrue(multibyte.getBytes(StandardCharsets.UTF_8).length > 65536);
		assertFalse(StremioWebSessionPolicy.isPlayerRoute(multibyte));
		assertFalse(StremioWebSessionPolicy.isPersistableRoute(DETAIL + "x".repeat(65536)));
	}

	@Test
	public void disconnectDropsTheRamOnlyRecoveryTarget() throws Exception {
		StremioWebView web = allocate(StremioWebView.class);
		web.loadFreshDocument(DETAIL);
		web.endAutomotiveSession();
		assertNull(pendingRecovery(web));
	}

	@Test
	public void reconnectCannotReviveThePreviousRecoveryTarget() throws Exception {
		StremioWebView web = allocate(StremioWebView.class);
		web.loadFreshDocument(DETAIL);
		web.resetToHomeForNewSession();
		assertNull(pendingRecovery(web));
	}

	@Test
	public void playerRouteCannotBeQueuedAsFreshDocumentRecovery() throws Exception {
		StremioWebView web = allocate(StremioWebView.class);
		web.loadFreshDocument(PLAYER);
		assertNull(pendingRecovery(web));
	}

	@Test
	public void cancellationClearsAQueuedTransferredPlayerRoute() throws Exception {
		StremioWebView web = allocate(StremioWebView.class);
		Field field = StremioWebView.class.getDeclaredField("pendingFreshDocumentUrl");
		field.setAccessible(true);
		field.set(web, PLAYER);
		web.cancelTransferredPlayer();
		assertNull(field.get(web));
	}

	@Test
	public void rendererRecoveryMustNotReloadThePlayerRoute() throws Exception {
		StremioWebView web = allocate(StremioWebView.class);
		web.loadUrl(PLAYER);
		// The production superclass would recover lastUrl, reloading the player after renderer loss.
		var method = me.aap.fermata.addon.web.FermataWebView.class
				.getDeclaredMethod("getRecoveryUrl");
		method.setAccessible(true);
		assertEquals(StremioWebSessionPolicy.HOME_URL, method.invoke(web));
	}

	@Test
	public void carBridgeCannotTakeThePhoneLeaseBeforeRelease() {
		var service = new ControlOnlySessionState();
		var phone = new StremioWebMediaSessionBridge(null);
		var car = new StremioWebMediaSessionBridge(null);
		long phoneLease = service.claim(phone, "phone:document");
		assertTrue(phoneLease > 0);
		assertEquals(0L, service.claim(car, "car:document"));
		assertTrue(service.isCurrent(phone, phoneLease));
		assertTrue(service.release(phone));
		long carLease = service.claim(car, "car:document");
		assertTrue(carLease > phoneLease);
		assertFalse(service.isCurrent(phone, phoneLease));
		assertFalse(service.release(phone));
		assertTrue(service.isCurrent(car, carLease));
		service.invalidate();
		assertFalse(service.isCurrent(car, carLease));
	}

	private static Object pendingRecovery(StremioWebView web) throws Exception {
		Field field = StremioWebView.class.getDeclaredField("pendingFreshDocumentUrl");
		field.setAccessible(true);
		return field.get(web);
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field field = unsafe.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
	}
}
