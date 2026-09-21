package me.aap.fermata.addon.web;

import static org.junit.Assert.*;
import static me.aap.fermata.addon.web.WebUrlSyncObserver.Provenance.*;

import android.webkit.WebView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Exercises the actual client callback adapter and real reducer; no Android WebView required. */
public class FermataWebClientUrlBindingTest {
	private static final String A = "https://example.org/a", B = "https://example.org/b";

	@Test public void unsafeDocumentHistoryAndReloadCannotBecomeGet() throws Exception {
		for (var provenance : List.of(POST, UNKNOWN, RECOVERY)) {
			Fixture f = new Fixture();
			f.mark(A, provenance);
			f.start(A);
			f.client.doUpdateVisitedHistory(null, B, false);
			f.client.doUpdateVisitedHistory(null, B, true);
			f.finish(B);
			f.drain();
			assertTrue(provenance.name(), f.urls.isEmpty());
		}
	}

	@Test public void newNavigationCancelsPendingBeforeAnyStartCallback() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A);
		f.start(A);
		f.mark(B, UNKNOWN);
		f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void offOriginDoesNotBecomeEnabledBetweenMarkAndStart() throws Exception {
		Fixture f = new Fixture();
		f.enabled(false, 1);
		f.client.markAppNavigation(A);
		f.enabled(true, 2);
		f.start(A);
		f.client.doUpdateVisitedHistory(null, A, true);
		f.finish(A);
		f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void restoreAndItsRedirectAreBaselineButNextUserGetIsEmitted() throws Exception {
		Fixture f = new Fixture();
		f.client.markRecoveryNavigation(A);
		f.start(A);
		f.start(B);
		f.client.doUpdateVisitedHistory(null, B, false);
		f.finish(B);
		f.drain();
		assertTrue(f.urls.isEmpty());
		f.client.markAppNavigation(A);
		f.start(A);
		f.finish(A);
		f.drain();
		assertEquals(List.of(A), f.urls);
	}

	@Test public void stableGetAllowsSameOriginHistoryButNotForeignDocument() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A);
		f.start(A);
		f.finish(A);
		f.drain();
		f.client.doUpdateVisitedHistory(null, B, false);
		f.drain();
		assertEquals(List.of(A, B), f.urls);
		f.client.doUpdateVisitedHistory(null, "https://foreign.example/", false);
		f.drain();
		assertEquals(List.of(A, B), f.urls);
	}

	@Test public void oldObserverIsClosedAndCannotDispatchAfterReplacement() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A);
		f.start(A);
		f.client.clearUrlObserver(f.observer);
		f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void restoredBaselineCannotEmitButExplicitAppLoadCan() throws Exception {
		Fixture f = new Fixture();
		f.client.baselineUrl(A);
		f.start(A);
		f.client.doUpdateVisitedHistory(null, A, false);
		f.finish(A);
		f.drain();
		assertTrue(f.urls.isEmpty());
		f.client.markAppNavigation(B);
		f.start(B);
		f.drain();
		assertEquals(List.of(B), f.urls);
	}

	@Test public void sourceMustBeResumedAttachedCurrentAndHaveAnActivity() {
		Object view = new Object(), other = new Object();
		assertTrue(WebBrowserFragment.isCurrentUrlSource(true, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.isCurrentUrlSource(false, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.isCurrentUrlSource(true, false, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.isCurrentUrlSource(true, true, false, view, view, 4, 4));
		assertFalse(WebBrowserFragment.isCurrentUrlSource(true, true, true, view, other, 4, 4));
		assertFalse(WebBrowserFragment.isCurrentUrlSource(true, true, true, view, view, 4, 5));
	}

	@Test public void hostReplacementBetweenMarkAndStartCannotRetargetNavigation() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A);
		f.state = new WebUrlSyncObserver.DispatchState(true, 2, 7, 2, 1, 1);
		f.start(A);
		f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void consumedRouteCancelsPendingBeforeYoutubeOwnerRuns() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A);
		f.start(A);
		f.invoke("cancelUrlNavigation", new Class<?>[0]);
		f.client.doUpdateVisitedHistory(null, B, false);
		f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void failedGetThenAutomaticRecoveryRedirectCannotRegainGet() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A);
		f.start(A);
		f.invoke("forwardFailed", new Class<?>[]{String.class}, A);
		f.client.markRecoveryNavigation(A);
		f.invoke("markRequestNavigation", new Class<?>[]{String.class, WebUrlSyncObserver.Provenance.class},
				B, REQUEST_GET);
		f.start(B);
		f.client.doUpdateVisitedHistory(null, B, true);
		f.finish(B);
		f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void explicitReloadOnlyReplaysKnownGetAndOnlyOnce() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A);
		f.start(A); f.finish(A); f.drain();
		f.client.markUserReload(A);
		f.start(A);
		f.client.doUpdateVisitedHistory(null, A, true);
		f.finish(A); f.drain();
		assertEquals(List.of(A, A), f.urls);
		f.mark(B, POST); f.start(B); f.finish(B);
		f.client.markUserReload(B); f.start(B);
		f.client.doUpdateVisitedHistory(null, B, true); f.drain();
		assertEquals(List.of(A, A), f.urls);
	}

	@Test public void oldGetRedirectCannotAdoptNewModeRevision() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A); f.start(A);
		f.enabled(true, 3);
		f.invoke("markRequestNavigation", new Class<?>[]{String.class, WebUrlSyncObserver.Provenance.class},
				B, REQUEST_GET);
		f.start(B); f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void lateFinishAfterErrorCannotReviveHistory() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A); f.start(A);
		f.invoke("forwardFailed", new Class<?>[]{String.class}, A);
		f.finish(A);
		f.client.doUpdateVisitedHistory(null, B, false); f.drain();
		assertTrue(f.urls.isEmpty());
	}

	@Test public void explicitUserNavigationToBaselineUrlIsStillNewIntent() throws Exception {
		Fixture f = new Fixture();
		f.client.baselineUrl(A);
		f.client.markAppNavigation(A); f.start(A); f.drain();
		assertEquals(List.of(A), f.urls);
	}

	@Test public void callbackForwardingKeepsHistoryAndFinishAckNonEmitting() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A); f.start(A); f.finish(A); f.drain();
		assertEquals(List.of(A), f.urls);
		f.client.doUpdateVisitedHistory(null, B, false); f.drain();
		assertEquals(List.of(A, B), f.urls);
		f.finish(B); f.drain();
		assertEquals(List.of(A, B), f.urls);
	}

	@Test public void hiddenObserverCancelsDebounceImmediately() throws Exception {
		Fixture f = new Fixture();
		f.client.markAppNavigation(A); f.start(A);
		f.observer.onHidden();
		f.drain();
		assertTrue(f.urls.isEmpty());
	}


	private static class Fixture {
		final FermataWebClient client = new FermataWebClient();
		final List<Runnable> tasks = new ArrayList<>();
		final List<String> urls = new ArrayList<>();
		WebUrlSyncObserver.DispatchState state = new WebUrlSyncObserver.DispatchState(true, 1, 7, 1, 1, 1);
		final WebUrlSyncObserver observer = new WebUrlSyncObserver(() -> state,
				(r, delay) -> { tasks.add(r); return () -> {}; }, c -> urls.add(c.url()));
		Fixture() { client.setUrlObserver(observer, 7); }
		void enabled(boolean enabled, long revision) {
			state = new WebUrlSyncObserver.DispatchState(enabled, 1, 7, 1, revision, 1);
		}
		void mark(String url, WebUrlSyncObserver.Provenance p) throws Exception {
			invoke("markNavigation", new Class<?>[]{String.class, WebUrlSyncObserver.Provenance.class}, url, p);
		}
		void start(String url) throws Exception { invoke("forwardStarted", new Class<?>[]{WebView.class, String.class}, null, url); }
		void finish(String url) throws Exception { invoke("forwardFinished", new Class<?>[]{String.class}, url); }
		void invoke(String name, Class<?>[] types, Object... args) throws Exception {
			Method method = FermataWebClient.class.getDeclaredMethod(name, types);
			method.setAccessible(true);
			method.invoke(client, args);
		}
		void drain() { var pending = new ArrayList<>(tasks); tasks.clear(); pending.forEach(Runnable::run); }
	}
}
