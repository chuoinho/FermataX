package me.aap.fermata.addon.web.stremio;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.auto.AutomotiveNavigationController;
import me.aap.fermata.auto.OpenOnCarKind;
import me.aap.fermata.auto.OpenOnCarMode;
import me.aap.fermata.auto.OpenOnCarRequest;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;

/** Stremio-specific configuration of the reusable hosted WebView shell. */
@Keep
@SuppressWarnings("unused")
public final class StremioWebFragment extends WebBrowserFragment {
	static final String SEARCH_URL = "https://web.stremio.com/#/search?search=";
	private final AutomotiveNavigationController navigation = AutomotiveNavigationController.get();
	private final OpenOnCarMode.Listener openOnCarListener = (available, enabled, revision) -> {
		StremioWebView web = stremioWebView();
		if (web != null) web.post(this::updateOpenOnCarCapture);
	};
	@Nullable
	private StremioWebView openOnCarSource;
	private long openOnCarSourceGeneration;
	private boolean openOnCarListenerRegistered;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.stremio_browser, container, false);
	}

	@Override
	protected void registerListeners(MainActivityDelegate activity) {
		super.registerListeners(activity);
		activity.addBroadcastListener(this, FRAGMENT_CHANGED);
		if (!openOnCarListenerRegistered) {
			openOnCarListenerRegistered = true;
			navigation.getOpenOnCarMode().addListener(openOnCarListener);
		}
		updateOpenOnCarCapture();
	}

	@Override
	protected void unregisterListeners(MainActivityDelegate activity) {
		if (openOnCarListenerRegistered) {
			openOnCarListenerRegistered = false;
			navigation.getOpenOnCarMode().removeListener(openOnCarListener);
		}
		detachOpenOnCarCapture();
		super.unregisterListeners(activity);
		activity.removeBroadcastListener(this);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate activity, long event) {
		super.onActivityEvent(activity, event);
		if (event == FRAGMENT_CHANGED) {
			updateOpenOnCarCapture();
		}
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		updateOpenOnCarCapture();
	}

	@Override
	public void onPause() {
		super.onPause();
		updateOpenOnCarCapture();
	}

	@Override
	public void onResume() {
		super.onResume();
		updateOpenOnCarCapture();
	}

	@Override
	public void onDestroyView() {
		detachOpenOnCarCapture();
		super.onDestroyView();
	}

	@Override
	protected void tryAttachUrlObserver() {
		super.tryAttachUrlObserver();
		updateOpenOnCarCapture();
	}

	@Override
	protected String getInitialUrl(@NonNull WebBrowserAddon addon) {
		return ((StremioWebAddon) addon).getEntryUrl();
	}

	@Override
	protected boolean hasHomeButton() {
		return true;
	}

	@Override
	protected void goHome() {
		FermataWebView web = getWebView();
		if (web instanceof StremioWebView stremio) stremio.loadFreshDocument(StremioWebAddon.HOME_URL);
		else if (web != null) web.loadUrl(StremioWebAddon.HOME_URL);
	}

	@Override
	public boolean onBackPressed() {
		FermataWebView web = getWebView();
		FermataChromeClient chrome = (web == null) ? null : web.getWebChromeClient();
		if ((web instanceof StremioWebView stremio) && ((chrome == null) || !chrome.isFullScreen())) {
			stremio.cancelTransferredPlayer();
			StremioWebAddon addon = (StremioWebAddon) getAddon();
			String target = StremioWebSessionPolicy.backTarget(web.getUrl(),
					(addon == null) ? StremioWebAddon.HOME_URL : addon.getPlayerBackTarget(),
					stremio.isPlayerActive());
			if (target != null) {
				stremio.loadFreshDocument(target);
				return true;
			}
			// Stremio Home is the add-on root. Do not return false here: Android treats that
			// as an unhandled Back and finishes the activity instead of returning to Fermata.
			if (StremioWebSessionPolicy.isHomeUrl(web.getUrl())) {
				MainActivityDelegate.get(requireContext()).showDashboard();
				return true;
			}
		}
		return super.onBackPressed();
	}

	@Override
	protected void onAutomotiveShutdown() {
		FermataWebView web = getWebView();
		if (web instanceof StremioWebView stremio) stremio.endAutomotiveSession();
		super.onAutomotiveShutdown();
	}

	@Override
	public AutomotiveNavigationController.OpenResult openOnCar(OpenOnCarRequest request,
			java.util.function.BooleanSupplier stillCurrent) {
		StremioWebView web = stremioWebView();
		MainActivityDelegate activity = (web == null) ? null :
				MainActivityDelegate.getActivityDelegate(web.getContext()).peek();
		if (!stillCurrent.getAsBoolean()) return AutomotiveNavigationController.OpenResult.CANCELLED;
		if ((request.kind() != OpenOnCarKind.STREMIO_PLAYER) ||
				(request.addonId() != me.aap.fermata.R.id.stremio_fragment) ||
				!(request.payload() instanceof String) || (web == null) || isHidden() ||
				(activity == null) || (activity.getActiveFragment() != this))
			return AutomotiveNavigationController.OpenResult.NOT_READY;
		MainActivityDelegate captured = activity;
		return web.openTransferredPlayer(request, () -> stillCurrent.getAsBoolean() && !isHidden() &&
				(stremioWebView() == web) && (MainActivityDelegate.getActivityDelegate(
						web.getContext()).peek() == captured) &&
				(captured.getActiveFragment() == this));
	}

	@Override
	protected void onAutomotiveSessionStarted() {
		super.onAutomotiveSessionStarted();
		FermataWebView web = getWebView();
		if ((web instanceof StremioWebView stremio) &&
				(getAddon() instanceof StremioWebAddon addon) && addon.requiresHomeOnNextEntry()) {
			stremio.resetToHomeForNewSession();
		}
	}

	private void updateOpenOnCarCapture() {
		StremioWebView web = stremioWebView();
		boolean eligible = (web != null) && web.isPhoneOpenOnCarSource() &&
				navigation.getOpenOnCarMode().isEnabled() && isOpenOnCarSourceForeground(web);
		if (!eligible) {
			detachOpenOnCarCapture();
			return;
		}
		if (openOnCarSource != web) {
			detachOpenOnCarCapture();
			openOnCarSource = web;
			long generation = ++openOnCarSourceGeneration;
			web.setOpenOnCarCapture(true, route -> onPlayerRouteSelected(web, generation, route));
		}
	}

	void onStremioDocumentStarted(StremioWebView web) {
		if (openOnCarSource != web) return;
		long generation = ++openOnCarSourceGeneration;
		web.setOpenOnCarCapture(true, route -> onPlayerRouteSelected(web, generation, route));
	}

	/** Intercepts a full document navigation before the phone WebView loads the Player route. */
	boolean interceptPlayerRoute(StremioWebView web, String route) {
		if (!StremioOpenOnCarPolicy.shouldInterceptMainFrameRoute(route,
				web.isPhoneOpenOnCarSource(), navigation.getOpenOnCarMode().isEnabled(),
				isOpenOnCarSourceForeground(web), web.isAttachedToWindow())) return false;
		long generation = openOnCarSourceGeneration;
		if (!isOpenOnCarSourceCurrent(web, generation)) return false;
		onPlayerRouteSelected(web, generation, route);
		return true;
	}

	void restoreAfterPlayerInterception(StremioWebView web) {
		StremioWebAddon addon = (StremioWebAddon) getAddon();
		String target = (addon == null) ? StremioWebAddon.HOME_URL : addon.getPlayerBackTarget();
		web.loadFreshDocument(target);
	}

	private void detachOpenOnCarCapture() {
		StremioWebView source = openOnCarSource;
		openOnCarSource = null;
		openOnCarSourceGeneration++;
		if (source != null) source.setOpenOnCarCapture(false, null);
	}

	private boolean isOpenOnCarSourceCurrent(StremioWebView web, long generation) {
		return StremioOpenOnCarPolicy.isCurrentPhoneSource(web.isPhoneOpenOnCarSource(),
				navigation.getOpenOnCarMode().isEnabled(), isOpenOnCarSourceForeground(web),
				web.isAttachedToWindow(),
				web, openOnCarSource, generation, openOnCarSourceGeneration);
	}

	private boolean isOpenOnCarSourceForeground(StremioWebView web) {
		MainActivityDelegate activity = MainActivityDelegate.getActivityDelegate(web.getContext()).peek();
		return !isHidden() && isResumed() && (activity != null) &&
				(activity.getActiveFragment() == this);
	}

	private void onPlayerRouteSelected(StremioWebView web, long generation, String route) {
		if (!StremioOpenOnCarPolicy.acceptsPlayerRoute(route) ||
				!isOpenOnCarSourceCurrent(web, generation)) return;
		var token = navigation.captureSourceToken(generation);
		navigation.open(new OpenOnCarRequest(OpenOnCarKind.STREMIO_PLAYER,
				me.aap.fermata.R.id.stremio_fragment, route, token),
				() -> isOpenOnCarSourceCurrent(web, generation)).onCompletion((result, failure) -> {
				if (!isOpenOnCarSourceCurrent(web, generation)) return;
			if ((failure != null) || (result != AutomotiveNavigationController.OpenResult.LOAD_DISPATCHED))
				UiUtils.showAlert(web.getContext(), R.string.stremio_open_on_car_failed);
		});
	}

	@Nullable
	private StremioWebView stremioWebView() {
		FermataWebView web = getWebView();
		return (web instanceof StremioWebView stremio) ? stremio : null;
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.stremio_fragment;
	}

	@Override
	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Override
	protected FermataWebClient createWebClient() {
		return new StremioWebClient();
	}

	@Override
	protected FermataChromeClient createChromeClient(FermataWebView webView,
			ViewGroup fullScreenView) {
		return new StremioChromeClient((StremioWebView) webView, fullScreenView);
	}

	@Override
	protected boolean supportsManualFullscreen(FermataWebView web, FermataChromeClient chrome) {
		return (web instanceof StremioWebView stremio) && stremio.supportsManualFullscreen();
	}

	@Override
	protected String getSearchUrl() {
		return SEARCH_URL;
	}

	void openSearch(String query) {
		StremioWebAddon addon = (StremioWebAddon) getAddon();
		if (addon != null) addon.beginExplicitNavigation();
		voiceCommand(new me.aap.fermata.ui.activity.VoiceCommand(query,
				me.aap.fermata.ui.activity.VoiceCommand.ACTION_FIND));
	}

	@Nullable
	@Override
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(StremioWebAddon.class);
	}
}
