package me.aap.fermata.addon.web;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import me.aap.fermata.auto.*;
import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;

/** Typed destination lifetime; never replays a page or treats dispatch as page completion. */
final class WebUrlDestination {
	private OpenOnCarRequest request;
	private BooleanSupplier current;
	private boolean failed;
	private boolean finished;
	private OpenOnCarToken startedToken;
	private final java.util.Set<String> loadedUrls = new java.util.HashSet<>();
	private boolean ambiguous;
	void pageStarted(String url) {
		if (!ambiguous && (request != null) && request.payload().equals(url) &&
				isCurrent(request.token())) startedToken = request.token();
		else startedToken = null;
	}
	boolean pageFinished(String url) {
		return (startedToken != null) && finish(startedToken, url);
	}
	void pageFailed(String url) {
		if ((request != null) && request.payload().equals(url)) fail(request.token());
	}
	void cancel() {
		request = null;
		current = null;
		startedToken = null;
	}
	OpenResult open(OpenOnCarRequest request, BooleanSupplier current,
			BooleanSupplier attached, Consumer<String> load) {
		if (!current.getAsBoolean()) return OpenResult.CANCELLED;
		if ((request.kind() != OpenOnCarKind.WEB_URL) ||
				(request.addonId() != me.aap.fermata.R.id.web_browser_fragment) ||
				!(request.payload() instanceof String url) || !WebUrlSyncPolicy.accepts(url))
			return OpenResult.FAILED;
		if (!attached.getAsBoolean()) return OpenResult.NOT_READY;
		if (!current.getAsBoolean()) return OpenResult.CANCELLED;
		this.request = request;
		this.current = current;
		// Native callbacks carry no navigation id. Never acknowledge an ambiguous repeated
		// URL using the newest token; cap RAM and fail closed once the lifetime cap is reached.
		ambiguous = (loadedUrls.size() >= 64) || !loadedUrls.add(url);
		startedToken = null;
		failed = finished = false;
		load.accept(url);
		return OpenResult.LOAD_DISPATCHED;
	}
	boolean finish(OpenOnCarToken token, String url) {
		if (!isCurrent(token) || failed || finished || !request.payload().equals(url)) return false;
		finished = true;
		return true;
	}
	boolean fail(OpenOnCarToken token) {
		if (!isCurrent(token)) return false;
		failed = true;
		finished = false;
		return true;
	}
	private boolean isCurrent(OpenOnCarToken token) {
		return (request != null) && request.token().equals(token) && current.getAsBoolean();
	}
}
