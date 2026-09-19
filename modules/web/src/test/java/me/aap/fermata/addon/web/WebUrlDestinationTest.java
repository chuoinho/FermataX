package me.aap.fermata.addon.web;

import static org.junit.Assert.*;
import static me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import me.aap.fermata.auto.*;

public class WebUrlDestinationTest {
	private static final String URL = "https://example.org/a?exact=1#part";
	private static OpenOnCarRequest request(long id) {
		return new OpenOnCarRequest(OpenOnCarKind.WEB_URL, me.aap.fermata.R.id.web_browser_fragment,
				URL, new OpenOnCarToken(1, 1, 1, 1, id));
	}
	@Test public void onlyAttachedCurrentExactTypedRequestLoads() {
		WebUrlDestination destination = new WebUrlDestination();
		List<String> loads = new ArrayList<>();
		assertEquals(NOT_READY, destination.open(request(1), () -> true, () -> false, loads::add));
		assertEquals(CANCELLED, destination.open(request(1), () -> false, () -> true, loads::add));
		assertEquals(FAILED, destination.open(new OpenOnCarRequest(OpenOnCarKind.WEB_URL, 77,
				URL, request(1).token()), () -> true, () -> true, loads::add));
		assertEquals(FAILED, destination.open(new OpenOnCarRequest(OpenOnCarKind.WEB_URL,
				me.aap.fermata.R.id.web_browser_fragment, new Object(), request(1).token()),
				() -> true, () -> true, loads::add));
		assertTrue(loads.isEmpty());
		assertEquals(LOAD_DISPATCHED, destination.open(request(1), () -> true, () -> true, loads::add));
		assertEquals(List.of(URL), loads);
	}
	@Test public void finalGuardRunsAfterAttachedCheck() {
		WebUrlDestination destination = new WebUrlDestination();
		AtomicBoolean current = new AtomicBoolean(true);
		List<String> loads = new ArrayList<>();
		assertEquals(CANCELLED, destination.open(request(1), current::get,
				() -> { current.set(false); return true; }, loads::add));
		assertTrue(loads.isEmpty());
	}
	@Test public void staleFailureOrCompletionCannotAcknowledgeNewToken() {
		WebUrlDestination destination = new WebUrlDestination();
		AtomicBoolean current = new AtomicBoolean(true);
		destination.open(request(1), () -> true, () -> true, u -> {});
		destination.open(request(2), current::get, () -> true, u -> {});
		assertFalse(destination.finish(request(1).token(), URL));
		assertFalse(destination.fail(request(1).token()));
		assertTrue(destination.fail(request(2).token()));
		assertFalse(destination.finish(request(2).token(), URL));
		destination.open(request(3), current::get, () -> true, u -> {});
		current.set(false);
		assertFalse(destination.finish(request(3).token(), URL));
	}
	@Test public void callbackAckNeedsCurrentStartAndRepeatedUrlIsAmbiguous() {
		WebUrlDestination destination = new WebUrlDestination();
		destination.open(request(1), () -> true, () -> true, u -> {});
		assertFalse(destination.pageFinished(URL));
		destination.pageStarted(URL);
		assertTrue(destination.pageFinished(URL));
		destination.open(request(2), () -> true, () -> true, u -> {});
		destination.pageStarted(URL);
		assertFalse(destination.pageFinished(URL));
	}
}
