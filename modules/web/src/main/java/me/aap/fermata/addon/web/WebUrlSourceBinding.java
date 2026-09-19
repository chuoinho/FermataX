package me.aap.fermata.addon.web;

import me.aap.fermata.auto.OpenOnCarMode;

/** Owns the existing client/reducer attachment and its mode subscription. */
final class WebUrlSourceBinding implements AutoCloseable {
	private final FermataWebClient client;
	private final WebUrlSyncObserver observer;
	private final OpenOnCarMode mode;
	private final OpenOnCarMode.Listener listener;
	WebUrlSourceBinding(FermataWebClient client, WebUrlSyncObserver observer,
			OpenOnCarMode mode, long generation, String baseline) {
		this.client = client;
		this.observer = observer;
		this.mode = mode;
		listener = (available, enabled, revision) -> observer.cancel();
		client.setUrlObserver(observer, generation);
		client.baselineUrl(baseline);
		mode.addListener(listener);
	}
	@Override public void close() {
		mode.removeListener(listener);
		client.clearUrlObserver(observer);
		observer.close();
	}
}
