package me.aap.fermata.addon.web;

import static org.junit.Assert.*;
import org.junit.Test;
import me.aap.fermata.ui.policy.RuntimeHostMode;

public class WebBrowserFragmentBindingTest {
	@Test public void actualBindingRestoresBaselineAndRearmsOnlyNewClientAfterReplacement() throws Exception {
		me.aap.fermata.auto.OpenOnCarMode mode = new me.aap.fermata.auto.OpenOnCarMode();
		mode.setAvailable(true, 1); mode.setEnabled(true);
		java.util.List<Runnable> tasks = new java.util.ArrayList<>();
		java.util.List<String> urls = new java.util.ArrayList<>();
		String baseline = "https://example.org/baseline";
		FermataWebClient old = new FermataWebClient();
		WebUrlSyncObserver observer = observer(tasks, urls, 7);
		WebUrlSourceBinding binding = new WebUrlSourceBinding(old, observer, mode, 7, baseline);
		old.doUpdateVisitedHistory(null, baseline, false);
		tasks.forEach(Runnable::run);
		assertTrue(urls.isEmpty());
		old.markAppNavigation("https://example.org/old"); start(old, "https://example.org/old");
		binding.close(); // same production lifetime operation used by hide/pause/destroy/replacement
		FermataWebClient replacement = old.createReplacement();
		WebUrlSourceBinding rebound = new WebUrlSourceBinding(replacement, observer(tasks, urls, 8), mode, 8, baseline);
		replacement.doUpdateVisitedHistory(null, baseline, false);
		old.markAppNavigation("https://example.org/stale"); start(old, "https://example.org/stale");
		tasks.forEach(Runnable::run);
		assertTrue(urls.isEmpty());
		replacement.markAppNavigation("https://example.org/new"); start(replacement, "https://example.org/new");
		tasks.forEach(Runnable::run);
		assertEquals(java.util.List.of("https://example.org/new"), urls);
		rebound.close();
	}

	private static WebUrlSyncObserver observer(java.util.List<Runnable> tasks,
			java.util.List<String> urls, long generation) {
		return new WebUrlSyncObserver(() -> new WebUrlSyncObserver.DispatchState(true, 1, generation, 1, 1, 1),
				(r, delay) -> { tasks.add(r); return () -> {}; }, c -> urls.add(c.url()));
	}
	private static void start(FermataWebClient client, String url) throws Exception {
		var method = FermataWebClient.class.getDeclaredMethod("forwardStarted", android.webkit.WebView.class, String.class);
		method.setAccessible(true); method.invoke(client, null, url);
	}
	@Test public void realBindingClosesClientAndUnsubscribesOnHideOrPause() {
		me.aap.fermata.auto.OpenOnCarMode mode = new me.aap.fermata.auto.OpenOnCarMode();
		mode.setAvailable(true, 1); mode.setEnabled(true);
		java.util.List<Runnable> pending = new java.util.ArrayList<>();
		java.util.List<String> urls = new java.util.ArrayList<>();
		int[] cancellations = {0};
		FermataWebClient client = new FermataWebClient();
		WebUrlSyncObserver observer = new WebUrlSyncObserver(
				() -> new WebUrlSyncObserver.DispatchState(true, 1, 7, 1, 1, 1),
				(r, delay) -> { pending.add(r); return () -> cancellations[0]++; },
				c -> urls.add(c.url()));
		WebUrlSourceBinding binding = new WebUrlSourceBinding(client, observer, mode, 7, null);
		observer.onStarted(new WebUrlSyncObserver.NavigationEvent(7, 10,
				WebUrlSyncObserver.Provenance.APP_GET, false, true, "https://example.org/a"));
		binding.close();
		assertEquals(1, cancellations[0]);
		pending.forEach(Runnable::run);
		assertTrue(urls.isEmpty());
		mode.setEnabled(false);
		assertEquals(1, cancellations[0]);
	}

	@Test public void actualModeSubscriptionCancelsImmediatelyOnOffAndDisconnect() {
		for (boolean disconnect : new boolean[]{false, true}) {
			me.aap.fermata.auto.OpenOnCarMode mode = new me.aap.fermata.auto.OpenOnCarMode();
			mode.setAvailable(true, 1); mode.setEnabled(true);
			int[] cancellations = {0};
			WebUrlSyncObserver observer = new WebUrlSyncObserver(
					() -> new WebUrlSyncObserver.DispatchState(true, 1, 7, 1, 1, 1),
					(r, delay) -> () -> cancellations[0]++, c -> { throw new AssertionError(); });
			WebUrlSourceBinding binding = new WebUrlSourceBinding(new FermataWebClient(), observer, mode, 7, null);
			observer.onStarted(new WebUrlSyncObserver.NavigationEvent(7, 10,
					WebUrlSyncObserver.Provenance.APP_GET, false, true, "https://example.org/a"));
			assertEquals(0, cancellations[0]);
			if (disconnect) mode.setAvailable(false, 2); else mode.setEnabled(false);
			assertEquals("cancel must run before any debounce runnable", 1, cancellations[0]);
			binding.close();
		}
	}
	@Test public void lifecycleAdmissionIsPhoneOnlyAndIdentityBound() {
		Object view = new Object();
		assertTrue(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.PHONE,
				true, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.youtube_fragment,
				me.aap.fermata.R.id.youtube_fragment, RuntimeHostMode.PHONE,
				true, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.stremio_fragment,
				me.aap.fermata.R.id.stremio_fragment, RuntimeHostMode.PHONE,
				true, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.MIRROR,
				true, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.PHONE,
				true, false, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.PHONE,
				true, true, false, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.AA_PROJECTION,
				true, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.PHONE,
				false, true, true, view, view, 4, 4));
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.PHONE,
				true, true, true, view, new Object(), 4, 4));
	}

	@Test public void replacementGenerationMustRebindOnlyAfterCurrentViewIsForeground() {
		Object oldView = new Object(), replacement = new Object();
		assertFalse(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.PHONE,
				true, true, true, oldView, replacement, 4, 5));
		assertTrue(WebBrowserFragment.shouldBindUrlObserver(
				me.aap.fermata.R.id.web_browser_fragment,
				me.aap.fermata.R.id.web_browser_fragment, RuntimeHostMode.PHONE,
				true, true, true, replacement, replacement, 5, 5));
	}
}
