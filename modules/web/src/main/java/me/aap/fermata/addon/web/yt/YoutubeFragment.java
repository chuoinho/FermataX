package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.util.Utils.dynCtx;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.external.ExternalPlaybackDelegateItem;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeFragment extends WebBrowserFragment implements FermataServiceUiBinder.Listener {
	private static final String DEFAULT_URL = "https://m.youtube.com";
	private static final Pref<LongSupplier> RESUME_POS = Pref.l("YT_RESUME_POS", 0L);
	private static final Pref<Supplier<String>> RESUME_VIDEO_ID = Pref.s("YT_RESUME_VIDEO_ID", "");
	private static final long[] HOST_RESTORE_RETRY_MS = {100L, 300L, 750L, 1500L, 3000L};
	private boolean playOnResume;
	private String pendingResumeVideoId;
	private boolean pendingResumePause;
	private YoutubeFullscreenCoordinator.Suspension hostFullscreenSuspension;
	private long hostSuspensionRelaunchGeneration;
	private long hostRestoreOperation;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.youtube_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		dynCtx(requireContext());
		return inflater.inflate(R.layout.youtube, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		YoutubeAddon addon = AddonManager.get().getAddon(YoutubeAddon.class);
		if (addon == null) return;

		String url;
		boolean pause;

		if (state != null) {
			url = state.getString("url", DEFAULT_URL);
			pause = state.getBoolean("pause", false);
		} else {
			url = addon.getLastYoutubeUrl();
			pause = false;
		}
		if ((url == null) || url.isBlank()) url = DEFAULT_URL;
		String startUrl = url;

		MainActivityDelegate.getActivityDelegate(view.getContext()).onSuccess(a -> {
			YoutubeWebView webView = a.findViewById(R.id.ytWebView);
			VideoView videoView = a.findViewById(R.id.ytVideoView);
			YoutubeWebClient webClient = new YoutubeWebClient();
			YoutubeChromeClient chromeClient = new YoutubeChromeClient(webView, videoView);
			webView.init(addon, webClient, chromeClient);
			registerListeners(a);
			pendingResumeVideoId = addon.getPreferenceStore().getStringPref(RESUME_VIDEO_ID);
			pendingResumePause = pause;
			boolean directPlayback = webView.consumeInitialPlaybackNavigationClaim();
			YoutubeAddon.SessionReturnAction returnAction =
					addon.consumePlaybackSessionReturn(System.currentTimeMillis());
			if (!directPlayback) {
				if (returnAction == YoutubeAddon.SessionReturnAction.RESET_HOME)
					webView.resetToHome();
				else webView.loadUrl(startUrl);
			}
			applyPendingResume(a, a.getMediaServiceBinder().getCurrentItem());
		});
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle state) {
		super.onSaveInstanceState(state);
		String url = getUrl();
		if (url != null) state.putString("url", url);
		WebBrowserAddon addon = getAddon();
		if (addon == null) return;
		MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(getContext()).peek();
		if (a == null) return;

		SharedPreferenceStore ps = addon.getPreferenceStore();
		MediaSessionCallback cb = a.getMediaSessionCallback();
		MediaEngine eng = cb.getEngine();

		if (eng instanceof YoutubeMediaEngine) {
			state.putBoolean("pause", !cb.isPlaying());
			String savedUrl = getUrl();
			try {
				YoutubeItem item = YoutubeItem.fromPageUrl(savedUrl, "", 0L);
				ps.applyStringPref(RESUME_VIDEO_ID, item.videoId());
			} catch (IllegalArgumentException ignored) {
				ps.removePref(RESUME_VIDEO_ID);
			}
			eng.getPosition().onSuccess(pos -> ps.applyLongPref(RESUME_POS, pos));
		} else {
			ps.removePref(RESUME_POS);
			ps.removePref(RESUME_VIDEO_ID);
		}
	}

	@Override
	public void onDestroyView() {
		YoutubeWebView view = getWebView();
		if (view != null) {
			discardHostFullscreenSuspension(view);
			view.setFullscreenTapEnabled(false);
		}
		unregisterListeners(MainActivityDelegate.get(requireContext()));
		super.onDestroyView();
	}

	@Override
	protected void registerListeners(MainActivityDelegate a) {
		super.registerListeners(a);
		a.addBroadcastListener(this, FRAGMENT_CHANGED);
		a.getMediaServiceBinder().addBroadcastListener(this);
	}

	protected void unregisterListeners(MainActivityDelegate a) {
		super.unregisterListeners(a);
		a.getMediaServiceBinder().removeBroadcastListener(this);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		super.onActivityEvent(a, e);
		if ((e == FRAGMENT_CHANGED) && (a.getActiveFragment() == this)) syncPlaybackStateSoon();
	}

	@Override
	public void onPause() {
		YoutubeWebView runtimeView = getWebView();
		if ((runtimeView != null) && runtimeView.usesAutoPlaybackBehavior()) {
			runtimeView.setFullscreenTapEnabled(false);
			suspendPlaybackPresentation(runtimeView);
		} else {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
				FermataServiceUiBinder b = a.getMediaServiceBinder();
				if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem()) && b.isPlaying()) {
					b.getMediaSessionCallback().onPause();
					playOnResume = true;
				} else {
					playOnResume = false;
				}
			});
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		YoutubeWebView view = getWebView();
		if ((view != null) && view.usesAutoPlaybackBehavior()) {
			syncPlaybackStateSoon();
			restorePlaybackPresentationSoon(view);
			return;
		}
		if (!playOnResume) return;
		playOnResume = false;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem())) {
				b.getMediaSessionCallback().onPlay();
			}
		});
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		YoutubeWebView view = getWebView();
		if ((view == null) || !view.usesAutoPlaybackBehavior()) return;
		if (hidden) {
			discardHostFullscreenSuspension(view);
			view.setFullscreenTapEnabled(false);
			view.leavePlaybackPresentation();
		} else {
			YoutubeAddon addon = AddonManager.get().getAddon(YoutubeAddon.class);
			if (addon != null) {
				YoutubeAddon.SessionReturnAction returnAction =
						addon.consumePlaybackSessionReturn(System.currentTimeMillis());
				PlayableItem current = MainActivityDelegate.get(requireContext())
						.getMediaServiceBinder().getCurrentItem();
				if ((returnAction == YoutubeAddon.SessionReturnAction.RESET_HOME) &&
						!YoutubeMediaEngine.isYoutubeItem(current)) view.resetToHome();
			}
			syncPlaybackStateSoon();
		}
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == R.id.fullscreen) {
			YoutubeWebView web = getWebView();
			return (web != null) && web.enterManualFullScreen();
		}
		return super.menuItemSelected(item);
	}

	private void syncPlaybackStateSoon() {
		YoutubeWebView v = getWebView();
		if (v == null) return;
		v.post(v::syncPlaybackState);
		v.postDelayed(v::syncPlaybackState, 600L);
	}

	private void suspendPlaybackPresentation(YoutubeWebView view) {
		hostRestoreOperation++;
		YoutubeFullscreenCoordinator.Suspension current = hostFullscreenSuspension;
		if ((current != null) && view.isPlaybackPresentationSuspensionCurrent(current)) return;

		hostFullscreenSuspension = view.suspendPlaybackPresentation();
		if (hostFullscreenSuspension != null) {
			hostSuspensionRelaunchGeneration =
					MainActivityDelegate.get(view.getContext()).getHostRelaunchGeneration();
		}
	}

	private void restorePlaybackPresentationSoon(YoutubeWebView view) {
		if (hostFullscreenSuspension == null) return;
		long operation = ++hostRestoreOperation;
		view.post(() -> restorePlaybackPresentation(view, operation, 0));
	}

	private void restorePlaybackPresentation(YoutubeWebView view, long operation, int attempt) {
		YoutubeFullscreenCoordinator.Suspension suspension = hostFullscreenSuspension;
		if ((suspension == null) || (operation != hostRestoreOperation)) return;

		MainActivityDelegate activity;
		try {
			activity = MainActivityDelegate.get(view.getContext());
		} catch (RuntimeException ignored) {
			discardHostFullscreenSuspension(view);
			return;
		}

		boolean youtubeActive = !isHidden() && (activity.getActiveFragment() == this);
		boolean youtubeOwnsPlayback =
				activity.getMediaSessionCallback().getEngine() instanceof YoutubeMediaEngine;
		YoutubeHostInterruptionPolicy.Decision decision = YoutubeHostInterruptionPolicy.resolve(
				hostSuspensionRelaunchGeneration, activity.getHostRelaunchGeneration(),
				activity.isHostResumed(), view.isAttachedToWindow(), youtubeActive,
				youtubeOwnsPlayback);

		if (decision == YoutubeHostInterruptionPolicy.Decision.DISCARD) {
			discardHostFullscreenSuspension(view);
			return;
		}
		if ((decision == YoutubeHostInterruptionPolicy.Decision.RESTORE) &&
				view.resumePlaybackPresentation(suspension)) {
			clearHostFullscreenSuspension();
			view.syncPlaybackState();
			return;
		}

		if ((attempt >= HOST_RESTORE_RETRY_MS.length) ||
				!view.isPlaybackPresentationSuspensionCurrent(suspension)) {
			discardHostFullscreenSuspension(view);
			return;
		}
		view.postDelayed(() -> restorePlaybackPresentation(view, operation, attempt + 1),
				HOST_RESTORE_RETRY_MS[attempt]);
	}

	private void discardHostFullscreenSuspension(@Nullable YoutubeWebView view) {
		YoutubeFullscreenCoordinator.Suspension suspension = hostFullscreenSuspension;
		clearHostFullscreenSuspension();
		if ((view != null) && (suspension != null))
			view.discardPlaybackPresentationSuspension(suspension);
	}

	private void clearHostFullscreenSuspension() {
		hostRestoreOperation++;
		hostFullscreenSuspension = null;
		hostSuspensionRelaunchGeneration = 0L;
	}

	public void loadUrl(String url) {
		FermataWebView v = getWebView();
		if (v != null) v.loadUrl(url);
	}

	@Override
	public void voiceCommand(VoiceCommand cmd) {
		YoutubeWebView v = getWebView();
		if (v != null) v.prepareVoiceSearch();
		super.voiceCommand(cmd);
	}

	boolean playVoiceSelection(String stableId) {
		if ((stableId == null) || !stableId.startsWith("youtube:")) return false;
		String videoId = stableId.startsWith("youtube:video:") ?
				stableId.substring("youtube:video:".length()) :
				stableId.substring("youtube:".length());
		if (!videoId.matches("[A-Za-z0-9_-]{6,32}")) return false;
		YoutubeWebView v = getWebView();
		if (v == null) return false;
		v.playVoiceVideo(videoId);
		return true;
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		applyPendingResume(activity, newItem);
		if (!YoutubeMediaEngine.isYoutubeItem(newItem)) {
			YoutubeWebView web = getWebView();
			if (web != null) {
				discardHostFullscreenSuspension(web);
				web.setFullscreenTapEnabled(false);
			}
		}
		if (isHidden()) return;
		if (activity.getActiveFragment() == this)
			YoutubeToolBarMediator.instance.updateVisibility(activity.getToolBar(), this);

		if (!YoutubeMediaEngine.isYoutubeItem(newItem) && YoutubeMediaEngine.isYoutubeItem(oldItem)) {
			FermataWebView v = getWebView();
			if (v == null) return;
			FermataChromeClient chrome = v.getWebChromeClient();
			if (chrome != null) chrome.exitFullScreen();
		}
	}

	@Override
	public void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
		if ((snapshot == null) || isHidden() ||
				!YoutubeMediaEngine.isYoutubeItem(snapshot.getItem()) || (getContext() == null)) return;
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		if (activity.getActiveFragment() == this)
			YoutubeToolBarMediator.instance.updateVisibility(activity.getToolBar(), this);
	}

	void onPageNavigationChanged() {
		if (isHidden() || (getContext() == null)) return;
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		if (activity.getActiveFragment() == this)
			YoutubeToolBarMediator.instance.updateVisibility(activity.getToolBar(), this);
	}

	private void applyPendingResume(MainActivityDelegate activity, @Nullable PlayableItem item) {
		String videoId = pendingResumeVideoId;
		if ((videoId == null) || videoId.isBlank() || (item == null) ||
				!item.getOrigId().equals("youtube:video:" + videoId)) return;
		YoutubeAddon addon = AddonManager.get().getAddon(YoutubeAddon.class);
		if (addon == null) return;
		PreferenceStore prefs = addon.getPreferenceStore();
		long position = prefs.getLongPref(RESUME_POS);
		prefs.removePref(RESUME_POS);
		prefs.removePref(RESUME_VIDEO_ID);
		pendingResumeVideoId = null;
		MediaSessionCallback callback = activity.getMediaSessionCallback();
		if (position > 0L) callback.onSeekTo(position);
		if (pendingResumePause) callback.onPause();
		pendingResumePause = false;
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return YoutubeToolBarMediator.instance;
	}

	@Override
	public boolean canScrollUp() {
		FermataWebView v = getWebView();
		if (v == null) return false;
		FermataChromeClient chrome = v.getWebChromeClient();
		return (chrome != null) && (chrome.isFullScreen() || (v.getScrollY() > 0));
	}

	@Override
	public boolean onBackPressed() {
		MainActivityDelegate activity = MainActivityDelegate.get(requireContext());
		YoutubeWebView v = getWebView();
		if ((v != null) && v.exitPlaybackFullScreenForBack()) return true;
		// The fallback app-video presentation can outlive the browser fullscreen callback.
		// Treat the Activity layout as the final source of truth for the first Back.
		if ((v != null) && activity.isVideoMode()) {
			v.leavePlaybackPresentation();
			return true;
		}
		MediaEngine engine = activity.getMediaSessionCallback().getEngine();
		PlayableItem owner = (engine instanceof YoutubeMediaEngine youtube) ?
				youtube.getExternalPlaybackOwner() : null;
		if (owner != null) {
			if (v != null) v.leavePlaybackPresentation();
			activity.goToItem(owner.getParent());
			return true;
		}
		BrowsableItem parent = externalPlaybackParent(
				activity.getMediaSessionCallback().getCurrentItem());
		if (parent != null) {
			if (v != null) v.leavePlaybackPresentation();
			activity.goToItem(parent);
			return true;
		}
		return super.onBackPressed();
	}

	@Nullable
	static BrowsableItem externalPlaybackParent(@Nullable PlayableItem item) {
		return (item instanceof ExternalPlaybackDelegateItem) ? item.getParent() : null;
	}

	@Nullable
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(YoutubeAddon.class);
	}

	@Nullable
	protected YoutubeWebView getWebView() {
		View v = getView();
		return (v != null) ? v.findViewById(R.id.ytWebView) : null;
	}

	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Override
	protected String getSearchUrl() {
		return "https://www.youtube.com/results?search_query=";
	}

	@Override
	protected boolean shouldRestoreFullScreenOnResume() {
		return false;
	}

	private static final class YoutubeToolBarMediator implements ToolBarView.Mediator.BackTitle {
		static final YoutubeToolBarMediator instance = new YoutubeToolBarMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			ToolBarView.Mediator.BackTitle.super.enable(tb, f);
			ImageButton fullScreen = addButton(tb, R.drawable.fullscreen, this, R.id.fullscreen);
			int minSize = Math.round(48f * tb.getResources().getDisplayMetrics().density);
			fullScreen.setMinimumWidth(minSize);
			fullScreen.setMinimumHeight(minSize);
			fullScreen.setContentDescription(tb.getContext().getString(
					me.aap.fermata.R.string.fullscreen_mode));
			updateVisibility(tb, f);
		}

		@Override
		public int getVisibility(ToolBarView tb, ActivityFragment f) {
			MainActivityDelegate a = MainActivityDelegate.get(tb.getContext());
			return !a.isBarsHidden() && (shouldShowBack(f) || shouldShowFullScreen(a, f)) ?
					VISIBLE : GONE;
		}

		@Override
		public void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
			ToolBarView.Mediator.BackTitle.super.onActivityEvent(tb, a, e);
			ActivityFragment f = a.getActiveFragment();
			if (f != null) updateVisibility(tb, f);
		}

		@Override
		public void onClick(View v) {
			if (v.getId() == R.id.fullscreen) {
				ActivityFragment fragment = ActivityDelegate.get(v.getContext()).getActiveFragment();
				if (fragment instanceof YoutubeFragment youtube) {
					YoutubeWebView web = youtube.getWebView();
					if (web != null) web.enterManualFullScreen();
				}
				return;
			}
			ActivityDelegate.get(v.getContext()).onBackPressed();
		}

		@Override
		public int getBackButtonVisibility(ActivityFragment f) {
			return shouldShowBack(f) ? VISIBLE : GONE;
		}

		private void updateVisibility(ToolBarView tb, ActivityFragment f) {
			boolean showBack = shouldShowBack(f);
			View b = tb.findViewById(getBackButtonId());
			if (b != null) b.setVisibility(showBack ? VISIBLE : GONE);
			MainActivityDelegate a = MainActivityDelegate.get(tb.getContext());
			boolean showFullScreen = shouldShowFullScreen(a, f);
			if (f instanceof YoutubeFragment youtube) {
				YoutubeWebView web = youtube.getWebView();
				if (web != null) web.setFullscreenTapEnabled(showFullScreen);
			}
			View fullScreen = tb.findViewById(R.id.fullscreen);
			if (fullScreen != null) fullScreen.setVisibility(showFullScreen ? VISIBLE : GONE);
			boolean visible = getVisibility(tb, f) == VISIBLE;
			tb.setVisibility(visible ? VISIBLE : GONE);
			if (!visible) return;

			PlaybackSnapshot snapshot = a.getMediaSessionCallback().getPlaybackSnapshot();
			TextView title = tb.findViewById(getTitleId());
			YoutubeWebView web = (f instanceof YoutubeFragment youtube) ?
					youtube.getWebView() : null;
			boolean playbackTitle = (web != null) && YoutubeToolbarPolicy.usePlaybackTitle(
					web.getUrl(), YoutubeMediaEngine.isYoutubeItem(snapshot.getItem()));
			if (title != null) title.setText(playbackTitle ? snapshot.getDisplayTitle() : f.getTitle());
		}

		private boolean shouldShowFullScreen(MainActivityDelegate activity, ActivityFragment f) {
			if (!(f instanceof YoutubeFragment youtube) ||
					(activity.getActiveFragment() != youtube) || activity.isVideoMode()) return false;
			YoutubeWebView web = youtube.getWebView();
			if ((web == null) || !web.isAttachedToWindow() ||
					(activity.getMediaSessionCallback().getEngine() != web.getMediaEngine())) return false;
			FermataChromeClient chrome = web.getWebChromeClient();
			return ((chrome == null) || !chrome.isFullScreen()) && YoutubeMediaEngine.isYoutubeItem(
					activity.getMediaServiceBinder().getCurrentItem());
		}

		private boolean shouldShowBack(ActivityFragment f) {
			if (!(f instanceof YoutubeFragment y)) return false;
			FermataWebView v = y.getWebView();
			if (v == null) return false;
			FermataChromeClient c = v.getWebChromeClient();
			MediaEngine engine = MainActivityDelegate.get(v.getContext())
					.getMediaSessionCallback().getEngine();
			boolean externalPlayback = (engine instanceof YoutubeMediaEngine youtube) &&
					(youtube.getExternalPlaybackOwner() != null);
			return YoutubeToolbarPolicy.showBack(externalPlayback,
					(c != null) && c.isFullScreen(), v.canGoBack(), y.isRootPage(), v.getUrl());
		}
	}
}
