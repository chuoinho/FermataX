package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_YT;
import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.media.AudioFocusRequestCompat;

import com.google.android.play.core.splitcompat.SplitCompat;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebClient.DiagnosticsObserver;
import me.aap.fermata.addon.web.FermataWebClient.DiagnosticsSnapshot;
import me.aap.fermata.addon.web.FermataWebClient.PlaybackEvent;
import me.aap.fermata.addon.web.yt.YoutubeAddon.VideoScale;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.addon.external.ExternalPlaybackDelegateItem;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * @author Andrey Pavlenko
 */
class YoutubeMediaEngine implements MediaEngine, OverlayMenu.SelectionHandler {
	private static final long FULLSCREEN_TAP_PAUSE_GUARD_MS = 750L;
	private static final String ID = "youtube";
	private static final String CURRENT_ID = ID + ":current";
	private static final String NEXT_ID = ID + ":next";
	private static final String PREV_ID = ID + ":prev";
	private static final String END_ID = ID + ":end";
	private final YoutubeWebView web;
	private final MediaSessionCallback cb;
	private final ExtRoot mediaRoot;
	private final YoutubePlayableItem next;
	private final YoutubePlayableItem prev;
	private final YoutubePlayableItem end;
	private final YoutubeFullscreenCoordinator fullScreenCoordinator;
	private final YoutubePlaybackIntentGate playbackIntentGate = new YoutubePlaybackIntentGate();
	private final YoutubeAdController adController = new YoutubeAdController();
	private final YoutubePlaybackSession playbackSession = new YoutubePlaybackSession();
	private final YoutubePlaybackOwner<PlayableItem> playbackOwner = new YoutubePlaybackOwner<>();
	private final YoutubePlaybackMetadata playbackMetadata;
	private final DiagnosticsObserver diagnosticsObserver;
	private YoutubePlayableItem current;
	private PlayableItem externalPlaybackOwner;
	private String externalPlaybackVideoId = "";
	private YoutubePlaybackSession.Snapshot playbackSessionSnapshot;
	private long adPlaybackGeneration;
	private Runnable adRetryTask;
	private String qualityUrl;
	private boolean ignorePause;
	private long touchStamp;
	private YoutubeMediaEngine transferSource;
	private String transferVideoId = "";
	private long transferPosition;
	private float transferSpeed = 1f;
	private boolean transferPlayRequested;
	private boolean transferPositionReady;
	private boolean transferTargetReady;
	private boolean webDestroyed;
	private boolean audibleStartPending;
	private long fullscreenTapPauseGuardUntil;
	private long fullscreenTapPauseGeneration;
	private String fullscreenTapPauseVideoId = "";
	private boolean muteDiagnosticKnown;
	private boolean lastMuteDiagnosticState;
	private long lastMuteDiagnosticGeneration;

	public YoutubeMediaEngine(YoutubeWebView web, MainActivityDelegate a) {
		this.web = web;
		playbackMetadata = web.getAddon().getPlaybackMetadata();
		diagnosticsObserver = FermataWebClient.diagnosticsObserver();
		cb = a.getMediaSessionCallback();
		fullScreenCoordinator = new YoutubeFullscreenCoordinator(
				new YoutubeFullscreenHostAdapter(this, web));
		mediaRoot = new ExtRoot("youtube", a.getLib(), AddonCapability.YOUTUBE);
		next = new TransportItem(NEXT_ID, mediaRoot, GenericFileSystem.getInstance().create("http://youtube.com/next"));
		prev = new TransportItem(PREV_ID, mediaRoot, GenericFileSystem.getInstance().create("http://youtube.com/prev"));
		end = new TransportItem(END_ID, mediaRoot, GenericFileSystem.getInstance().create("http://youtube.com/end")) {
			@NonNull
			@Override
			public FutureSupplier<PlayableItem> getNextPlayable() {
				return completed(next);
			}
		};
	}

	void ready(String url) {
		if (!YoutubePlaybackMetadata.isStructuredSignal(url)) return;
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse(url, web.getUrl());
		recordPlaybackSignal(PlaybackEvent.READY_SIGNAL, signal, false);
		if (!acceptsPlaybackGeneration(url)) {
			recordPlaybackSignal(PlaybackEvent.SIGNAL_REJECTED, signal, false);
			if (rejectsExternalAutoNext(url)) web.silenceRejectedPlayback(url);
			return;
		}
		if (isCurrentEngine()) {
			url = setCurrent(url);
			completePendingTransfer();
			resumeAudibleStartIfPending();
			requestAutoVideoMode(url);
			return;
		}
		if (!web.usesAutoPlaybackBehavior()) return;
		boolean activeHost = isActivePlaybackHost();
		boolean intentAccepted = activeHost && acceptsPlaybackSignal();
		if (!canClaimExternalPlayback(false, activeHost, true, intentAccepted)) {
			boolean forwarded = YoutubePlaybackHostPolicy.forward(web, false, activeHost, signal, this::acceptsPlaybackSignal);
			recordPlaybackSignal(PlaybackEvent.SIGNAL_REJECTED, signal, false);
			Log.d(forwarded ? "Forwarding YouTube playback to automotive host" :
					"Ignoring YouTube ready signal without playback intent");
			web.silenceRejectedPlayback(url);
			return;
		}

		url = setCurrent(url);
		if (cb.startExternalPlayback(this)) {
			requestAutoVideoMode(url);
			web.playAudible();
		}
	}

	void playing(String url) {
		if (!YoutubePlaybackMetadata.isStructuredSignal(url)) return;
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse(url, web.getUrl());
		recordPlaybackSignal(PlaybackEvent.PLAYING_SIGNAL, signal, true);
		if (!acceptsPlaybackGeneration(url)) {
			recordPlaybackSignal(PlaybackEvent.SIGNAL_REJECTED, signal, false);
			if (rejectsExternalAutoNext(url)) web.silenceRejectedPlayback(url);
			return;
		}
		boolean currentEngine = isCurrentEngine();
		boolean activeHost = currentEngine || isActivePlaybackHost();
		boolean autoPlayback = web.usesAutoPlaybackBehavior();
		boolean intentAccepted = !autoPlayback || currentEngine ||
				(activeHost && acceptsPlaybackSignal());
		if (!canClaimExternalPlayback(currentEngine, activeHost, autoPlayback,
				intentAccepted)) {
			boolean forwarded = YoutubePlaybackHostPolicy.forward(web, currentEngine, activeHost, signal, this::acceptsPlaybackSignal);
			recordPlaybackSignal(PlaybackEvent.SIGNAL_REJECTED, signal, false);
			Log.d(forwarded ? "Forwarding YouTube playback to automotive host" :
					"Ignoring YouTube preview playback");
			web.silenceRejectedPlayback(url);
			return;
		}
		url = setCurrent(url);
		completePendingTransfer();
		resumeAudibleStartIfPending();
		if (!web.getAddon().autoHighestQuality()) {
			qualityUrl = null;
		} else if (!url.isEmpty() && !url.equals(qualityUrl)) {
			qualityUrl = url;
			web.setHighestVideoQuality();
		}
		boolean claimed = cb.startExternalPlayback(this);
		if (claimed || isCurrentEngine()) requestAutoVideoMode(url);
		recordPlaybackSignal(PlaybackEvent.OWNERSHIP_ADOPTED, signal, true);
		if (shouldRestoreAudibleAfterClaim(currentEngine, claimed)) web.playAudible();
		web.onYoutubePlaybackResumed();
	}

	void adSignal(String data) {
		if ((adPlaybackGeneration <= 0L) || !isCurrentEngine() || (data == null)) return;
		YoutubeAdSignal signal = YoutubeAdSignal.parse(data);
		if ((signal == null) || (playbackSessionSnapshot == null) ||
				signal.generation() != playbackSessionSnapshot.generation() ||
				!matchesCurrentPage(signal.pageUrl())) return;

		YoutubeAdController.Transition transition;
		switch (signal.phase()) {
			case POD_START -> transition = adController.onAdPodStarted(adPlaybackGeneration,
					new YoutubeAdController.AdPod(signal.podId(),
							adController.isContentStarted() ? YoutubeAdController.BreakType.MID_ROLL :
									YoutubeAdController.BreakType.PRE_ROLL, -1));
			case AD_START -> transition = adController.onAdStarted(adPlaybackGeneration,
					signal.podId(), signal.adId());
			case AD_ERROR -> transition = adController.onAdError(adPlaybackGeneration,
					signal.podId(), signal.adId(), true, SystemClock.uptimeMillis());
			case AD_COMPLETE -> transition = adController.onAdCompleted(adPlaybackGeneration,
					signal.podId(), signal.adId());
			case POD_COMPLETE -> transition = adController.onAdPodCompleted(adPlaybackGeneration,
					signal.podId());
			case CONTENT -> transition = adController.onContentStarted(adPlaybackGeneration);
			default -> {
				return;
			}
		}
		boolean resumedContent = applyAdTransition(transition);
		if ((signal.phase() == YoutubeAdSignal.Phase.CONTENT) && transition.accepted() &&
				!resumedContent)
			refreshContentMetadata();
		if (transition.state() == YoutubeAdController.State.COOLDOWN)
			scheduleAdRetry(transition.generation());
		else cancelAdRetry();
	}

	private boolean applyAdTransition(YoutubeAdController.Transition transition) {
		boolean resumedContent = false;
		for (YoutubeAdController.Effect effect : transition.effects()) {
			if (effect.type() == YoutubeAdController.EffectType.START_AD) {
				requestAdSkip(false);
			} else if (effect.type() == YoutubeAdController.EffectType.RETRY_AD_POD) {
				requestAdSkip(true);
			} else if (effect.type() == YoutubeAdController.EffectType.RESUME_CONTENT) {
				refreshContentMetadata();
				resumedContent = true;
			}
		}
		return resumedContent;
	}

	private void refreshContentMetadata() {
		if (!(current instanceof Current active) || !isCurrentEngine()) return;
		active.invalidateMetadata();
		cb.onEngineMetadataChanged(this);
	}

	private void requestAdSkip(boolean retry) {
		if (!web.getAddon().skipAd() || !(current instanceof Current active) ||
				(active.descriptor == null)) return;
		YoutubePlaybackSession.Snapshot playback =
				playbackSnapshot(active.descriptor.videoId());
		if (playback == null) return;
		if (retry) web.retryCurrentAd(playback.generation(), active.descriptor.videoId());
		else web.skipCurrentAd(playback.generation(), active.descriptor.videoId());
	}

	private void scheduleAdRetry(long generation) {
		cancelAdRetry();
		long delay = Math.max(0L, adController.getCooldownUntil() - SystemClock.uptimeMillis());
		adRetryTask = () -> {
			adRetryTask = null;
			if ((generation != adPlaybackGeneration) || !isCurrentEngine()) return;
			YoutubeAdController.Transition transition =
					adController.onClock(generation, SystemClock.uptimeMillis());
			applyAdTransition(transition);
			if (transition.state() == YoutubeAdController.State.COOLDOWN)
				scheduleAdRetry(generation);
		};
		web.postDelayed(adRetryTask, delay);
	}

	private void cancelAdRetry() {
		if (adRetryTask == null) return;
		web.removeCallbacks(adRetryTask);
		adRetryTask = null;
	}

	private void endAdPlayback() {
		cancelAdRetry();
		if (adPlaybackGeneration > 0L) adController.endPlayback(adPlaybackGeneration);
		adPlaybackGeneration = 0L;
	}

	private boolean matchesCurrentPage(String pageUrl) {
		if ((current == null) || (pageUrl == null) || pageUrl.isBlank()) return false;
		try {
			me.aap.fermata.addon.web.yt.YoutubeItem item =
					me.aap.fermata.addon.web.yt.YoutubeItem.fromPageUrl(pageUrl, "", 0L);
			return (current instanceof Current active) && active.matches(item, pageUrl);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	void ended(String signal) {
		if (!matchesCurrentSignal(signal)) return;
		recordPlaybackSignal(PlaybackEvent.ENDED_SIGNAL,
				YoutubePlaybackMetadata.parse(signal, web.getUrl()), false);
		web.prepareAutoNextAudioRestore(YoutubePlaybackMetadata.parse(signal, web.getUrl()));
		endAdPlayback();
		if (playbackSessionSnapshot != null) {
			playbackSession.finish(playbackSessionSnapshot);
			playbackSessionSnapshot = null;
			if (!webDestroyed) web.clearPlaybackGeneration();
		}
		current = end;
		qualityUrl = null;
		boolean currentEngine = isCurrentEngine();
		if (currentEngine) cb.onEngineEnded(this);
	}

	void paused(String signal) {
		if (!isCurrentEngine() || !matchesCurrentSignal(signal)) return;
		recordPlaybackSignal(PlaybackEvent.PAUSED_SIGNAL,
				YoutubePlaybackMetadata.parse(signal, web.getUrl()), false);
		if (consumeFullscreenTapPauseGuard(signal)) {
			web.play();
			return;
		}
		web.onYoutubePlaybackPaused();
		ignorePause = true;
		cb.onPause();
		ignorePause = false;
	}

	void touched() {
		if (web.usesAutoPlaybackBehavior())
			playbackIntentGate.armUserGesture(SystemClock.uptimeMillis());
		if (!web.usesAutoPlaybackBehavior() || !isCurrentEngine()) return;
		long now = System.currentTimeMillis();
		if ((now - touchStamp) < 350L) return;
		touchStamp = now;
		web.post(() -> {
			if (!isCurrentEngine()) return;
			MainActivityDelegate.get(web.getContext()).getControlPanel().onTouch(null);
		});
	}

	void fullscreenTapped(String signal) {
		if (!web.usesAutoPlaybackBehavior() || !matchesCurrentSignal(signal) ||
				!isFullscreenTapEligible()) return;
		YoutubePlaybackMetadata.Signal parsed = YoutubePlaybackMetadata.parse(signal, web.getUrl());
		fullscreenTapPauseGeneration = parsed.generation();
		fullscreenTapPauseVideoId = parsed.videoId();
		fullscreenTapPauseGuardUntil = SystemClock.uptimeMillis() + FULLSCREEN_TAP_PAUSE_GUARD_MS;
		if (!fullScreenCoordinator.enterManualAppFullscreen()) {
			clearFullscreenTapPauseGuard();
			return;
		}
		web.setFullscreenTapEnabled(false);
	}

	void onPlaybackGesture(long eventTime) {
		if (web.usesAutoPlaybackBehavior()) playbackIntentGate.armUserGesture(eventTime);
	}

	void armExplicitPlayback() {
		if (!web.usesAutoPlaybackBehavior()) return;
		armPlaybackIntent();
		fullScreenCoordinator.authorizeExplicitSelection();
	}

	private void armPlaybackIntent() {
		playbackIntentGate.armExplicitPlayback();
		// The target video can be created after the click/navigation event. Keep the audible
		// request until READY/PLAYING is emitted by that exact playback generation.
		audibleStartPending = true;
	}

	void onPageLoaded(String pageUrl) {
		YoutubePlaybackSession.Snapshot snapshot = playbackSessionSnapshot;
		if (!isCurrentEngine() || (snapshot == null) || !playbackSession.isCurrent(snapshot)) return;
		try {
			YoutubeItem item = YoutubeItem.fromPageUrl(pageUrl, "", 0L);
			if (snapshot.item().videoId().equals(item.videoId()))
				web.rebindPlaybackGeneration(snapshot.generation());
		} catch (IllegalArgumentException ignored) {
		}
	}

	long playbackGenerationSeed() {
		YoutubePlaybackSession.Snapshot snapshot = playbackSessionSnapshot;
		return isCurrentEngine() && (snapshot != null) && playbackSession.isCurrent(snapshot) ?
				snapshot.generation() : 0L;
	}

	void onUserExitFullScreen() {
		fullScreenCoordinator.onUserExit();
	}

	YoutubeFullscreenCoordinator.Suspension suspendFullscreenForHostInterruption() {
		return fullScreenCoordinator.suspendForHostInterruption();
	}

	boolean resumeFullscreenAfterHostInterruption(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		return fullScreenCoordinator.resumeAfterHostInterruption(suspension);
	}

	void discardFullscreenHostInterruption(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		fullScreenCoordinator.discardHostInterruption(suspension);
	}

	boolean isFullscreenHostInterruptionCurrent(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		return fullScreenCoordinator.isCurrent(suspension);
	}

	boolean onPlayerBack(boolean appVideoMode, boolean browserFullScreen) {
		return fullScreenCoordinator.onPlayerBack(
				isCurrentEngine(), appVideoMode, browserFullScreen);
	}

	boolean acceptsBrowserFullScreen(long request) {
		return fullScreenCoordinator.acceptBrowserEntry(request);
	}

	void onBrowserFullScreenChanged(boolean fullScreen) {
		fullScreenCoordinator.onBrowserVisibilityChanged(fullScreen);
	}

	boolean isFallbackFullScreenActive() {
		return fullScreenCoordinator.isFallbackPresentationActive();
	}

	long grantManualFullScreenEntry() {
		return fullScreenCoordinator.grantManualBrowserEntry();
	}

	void expireManualFullScreenEntry(long permit) {
		fullScreenCoordinator.expireManualBrowserEntry(permit);
	}

	boolean enterManualAppFullScreen() {
		return fullScreenCoordinator.enterManualAppFullscreen();
	}

	@Override
	public int getId() {
		return MEDIA_ENG_YT;
	}

	@Override
	public void prepare(PlayableItem source) {
		source = PlayableItemResolver.unwrap(source);
		if (source == next) {
			if (web.usesAutoPlaybackBehavior()) armPlaybackIntent();
			web.next();
		} else if (source == prev) {
			if (web.usesAutoPlaybackBehavior()) armPlaybackIntent();
			web.prev();
		} else {
			YoutubeDescriptorItem descriptorItem = descriptorItem(source);
			if (web.usesAutoPlaybackBehavior()) {
				if ((descriptorItem != null) && !(source instanceof Current)) armExplicitPlayback();
				else armPlaybackIntent();
			}
			if (descriptorItem != null) {
				YoutubeItem descriptor = descriptorItem.getYoutubeDescriptor();
				playbackOwner.prepare(source, descriptor.videoId(),
						!(source instanceof Current) && !(source instanceof ExternalPlaybackDelegateItem));
				if (source instanceof ExternalPlaybackDelegateItem) {
					externalPlaybackOwner = source;
					externalPlaybackVideoId = descriptor.videoId();
				} else {
					clearExternalPlaybackOwner();
				}
				current = (source instanceof Current) ? (YoutubePlayableItem) source :
						new Current(descriptor);
				if ((playbackSessionSnapshot == null) ||
						!playbackSession.isCurrent(playbackSessionSnapshot) ||
						!playbackSessionSnapshot.item().videoId().equals(descriptor.videoId())) {
					playbackSessionSnapshot = playbackSession.begin(descriptor,
							System.currentTimeMillis());
					recordPlayback(PlaybackEvent.SESSION_STARTED, playbackSessionSnapshot,
							false, false, false, false, false, 0L);
				}
				cancelAdRetry();
				adPlaybackGeneration = adController.beginPlayback(current.getId()).generation();
				if (matchesLoadedPage(descriptor.pageUrl())) {
					web.rebindPlaybackGeneration(playbackSessionSnapshot.generation());
				} else if (!(source instanceof Current)) {
					web.loadUrl(descriptor.pageUrl());
				}
			} else if (source instanceof Current) {
				current = (YoutubePlayableItem) source;
			}
			cb.onEnginePrepared(this);
		}
	}

	@Override
	public void start() {
		audibleStartPending = true;
		recordPlayback(PlaybackEvent.AUDIBLE_START_REQUESTED, playbackSessionSnapshot,
				true, false, false, false, false, 0L);
		if (transferSource != null) {
			transferPlayRequested = true;
			transferSource.start();
		}
		if (!webDestroyed) web.playAudible();
	}

	private void resumeAudibleStartIfPending() {
		if (!audibleStartPending || webDestroyed || !isCurrentEngine()) return;
		audibleStartPending = false;
		web.playAudible();
	}

	@Override
	public void stop() {
		recordPlayback(PlaybackEvent.OWNERSHIP_LOST, playbackSessionSnapshot,
				false, false, false, false, false, 0L);
		audibleStartPending = false;
		clearFullscreenTapPauseGuard();
		web.setFullscreenTapEnabled(false);
		boolean suppressSessionExpiry = web.consumeSessionExpirySuppression();
		boolean markSessionLeft = isCurrentEngine() && (current != null) && (current != end) &&
				!suppressSessionExpiry;
		playbackIntentGate.reset();
		finishPendingTransferSource();
		if ((current != null) && (current != end)) {
			endAdPlayback();
			current = null;
			qualityUrl = null;
			if (!webDestroyed) web.stop();
		}
		playbackSession.invalidate();
		recordPlayback(PlaybackEvent.SESSION_INVALIDATED, playbackSessionSnapshot,
				false, false, false, false, false, 0L);
		playbackSessionSnapshot = null;
		muteDiagnosticKnown = false;
		if (!webDestroyed) web.clearPlaybackGeneration();
		web.onYoutubePlaybackOwnershipLost();
		fullScreenCoordinator.cancelPlayback();
		clearExternalPlaybackOwner();
		playbackOwner.clear();
		if (markSessionLeft && !webDestroyed)
			web.getAddon().markPlaybackSessionLeft(System.currentTimeMillis());
	}

	@Override
	public void pause() {
		audibleStartPending = false;
		if (transferSource != null) {
			transferPlayRequested = false;
			transferSource.pause();
		}
		if (!ignorePause && !webDestroyed) web.pause();
	}

	@Override
	public PlayableItem getSource() {
		return (externalPlaybackOwner != null) ? externalPlaybackOwner : playbackOwner.resolve(current);
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		if (transferSource != null) return transferSource.getDuration();
		if (webDestroyed) {
			long duration = (current instanceof Current active && active.descriptor != null) ?
					active.descriptor.durationMillis() : 0L;
			return completed(duration);
		}
		long fallback = (current instanceof Current active && active.descriptor != null) ?
				active.descriptor.durationMillis() : 0L;
		return web.getContentDuration().map(duration -> (duration > 0L) ? duration : fallback);
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		if (transferSource != null) return transferSource.getPosition();
		if (webDestroyed) return completed(Math.max(0L, cb.getPlaybackState().getPosition()));
		return web.getPosition();
	}

	@Override
	public void setPosition(long position) {
		if (transferSource != null) {
			transferPosition = Math.max(0L, position);
			transferSource.setPosition(position);
		}
		if (!webDestroyed) web.setPosition(position);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		if (transferSource != null) return transferSource.getSpeed();
		if (webDestroyed) {
			float speed = cb.getPlaybackState().getPlaybackSpeed();
			return completed((speed > 0f) ? speed : 1f);
		}
		return web.getSpeed();
	}

	@Override
	public void setSpeed(float speed) {
		if (transferSource != null) {
			transferSpeed = speed;
			transferSource.setSpeed(speed);
		}
		if (!webDestroyed) web.setSpeed(speed);
	}

	@Override
	public void setVideoView(VideoView view) {
	}

	@Override
	public float getVideoWidth() {
		return 0;
	}

	@Override
	public float getVideoHeight() {
		return 0;
	}

	@Override
	public void close() {
		recordPlayback(PlaybackEvent.OWNERSHIP_LOST, playbackSessionSnapshot,
				false, false, false, false, false, 0L);
		clearFullscreenTapPauseGuard();
		web.setFullscreenTapEnabled(false);
		playbackIntentGate.reset();
		finishPendingTransferSource();
		if ((current != null) && (current != end)) {
			endAdPlayback();
			current = null;
			qualityUrl = null;
			if (!webDestroyed) web.stop();
		}
		playbackSession.invalidate();
		recordPlayback(PlaybackEvent.SESSION_INVALIDATED, playbackSessionSnapshot,
				false, false, false, false, false, 0L);
		playbackSessionSnapshot = null;
		muteDiagnosticKnown = false;
		if (!webDestroyed) web.clearPlaybackGeneration();
		web.onYoutubePlaybackOwnershipLost();
		fullScreenCoordinator.cancelPlayback();
		clearExternalPlaybackOwner();
		playbackOwner.clear();
	}

	@Override
	public boolean requestAudioFocus(@Nullable AudioManager audioManager,
																	 @Nullable AudioFocusRequestCompat audioFocusReq) {
		return true;
	}

	@Override
	public void releaseAudioFocus(@Nullable AudioManager audioManager,
																@Nullable AudioFocusRequestCompat audioFocusReq) {
	}

	private void requestAutoVideoMode(String mediaUrl) {
		if (!web.usesAutoPlaybackBehavior()) return;
		fullScreenCoordinator.requestAutoEntry((current instanceof Current c) ? c.pageUrl : web.getUrl(), mediaUrl);
	}

	private void recordPlaybackSignal(PlaybackEvent event,
			YoutubePlaybackMetadata.Signal signal, boolean playing) {
		YoutubePlaybackSession.Snapshot snapshot = playbackSessionSnapshot;
		boolean muteKnown = (signal != null) && (signal.volume() >= 0d);
		boolean muted = (signal != null) && signal.muted();
		recordPlayback(event, snapshot, playing, muteKnown, muted, false, false,
				(signal == null) ? 0L : signal.generation());
		if (muteKnown) recordMuteTransition(signal);
	}

	private void recordMuteTransition(YoutubePlaybackMetadata.Signal signal) {
		long generation = Math.max(0L, signal.generation());
		if (muteDiagnosticKnown && (lastMuteDiagnosticState == signal.muted()) &&
				(lastMuteDiagnosticGeneration == generation)) return;
		muteDiagnosticKnown = true;
		lastMuteDiagnosticState = signal.muted();
		lastMuteDiagnosticGeneration = generation;
		recordPlayback(PlaybackEvent.MUTE_CHANGED, playbackSessionSnapshot,
				!signal.muted(), true, signal.muted(), false, false, generation);
	}

	private void recordPlayback(PlaybackEvent event,
			YoutubePlaybackSession.Snapshot snapshot, boolean playing, boolean muteKnown,
			boolean muted, boolean resultKnown, boolean accepted, long signalGeneration) {
		try {
			long sessionGeneration = (snapshot == null) ? 0L : snapshot.generation();
			diagnosticsObserver.onPlayback(event, DiagnosticsSnapshot.builder()
					.state(event).result(resultKnown, accepted)
					.web(web.isShown(), web.isAttachedToWindow(), web.getWidth(), web.getHeight())
					.ownsPlayback(isCurrentEngine()).playing(playing)
					.mute(muteKnown, muted).generation(Math.max(sessionGeneration, signalGeneration))
					.revision(sessionGeneration).build());
		} catch (RuntimeException ignored) {
			// Diagnostics must never affect YouTube playback behavior.
		}
	}

	private boolean acceptsPlaybackSignal() {
		return playbackIntentGate.accepts(web.getUrl(), SystemClock.uptimeMillis(), isCurrentEngine());
	}

	private boolean isActivePlaybackHost() {
		try {
			MainActivityDelegate activity = MainActivityDelegate.get(web.getContext());
			return YoutubePlaybackHostPolicy.isPreferredHost(web) &&
					(activity.getActiveFragment() instanceof YoutubeFragment youtube) &&
					(youtube.getWebView() == web);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	static boolean canClaimExternalPlayback(boolean currentEngine, boolean activePlaybackHost,
			boolean autoBuild, boolean playbackIntentAccepted) {
		if (currentEngine) return true;
		if (!activePlaybackHost) return false;
		return !autoBuild || playbackIntentAccepted;
	}

	static boolean shouldRestoreAudibleAfterClaim(boolean wasCurrentEngine, boolean claimed) {
		return claimed && !wasCurrentEngine;
	}

	private boolean acceptsPlaybackGeneration(String data) {
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse(data, web.getUrl());
		if (!YoutubePlaybackMetadata.hasTrustedPlaybackIdentity(data, signal)) return false;
		if (!acceptsExternalPlaybackVideo(externalPlaybackVideoId, signal.videoId())) return false;
		boolean matchesLoadedPage = matchesLoadedPage(signal.pageUrl());
		if (!matchesLoadedPage) return false;
		long signalGeneration = YoutubePlaybackMetadata.playbackGeneration(data);
		YoutubePlaybackSession.Snapshot snapshot = playbackSessionSnapshot;
		if (snapshot == null) return signalGeneration == 0L && matchesLoadedPage;
		if (!playbackSession.isCurrent(snapshot) ||
				signalGeneration != snapshot.generation()) return false;
		if (snapshot.item().videoId().equals(signal.videoId())) return true;
		return isCurrentEngine() && matchesLoadedPage;
	}

	private boolean rejectsExternalAutoNext(String data) {
		if (externalPlaybackOwner == null) return false;
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse(data, web.getUrl());
		return !acceptsExternalPlaybackVideo(externalPlaybackVideoId, signal.videoId());
	}

	static boolean acceptsExternalPlaybackVideo(String ownerVideoId, String signalVideoId) {
		return (ownerVideoId == null) || ownerVideoId.isBlank() ||
				ownerVideoId.equals(signalVideoId);
	}

	private boolean matchesLoadedPage(String pageUrl) {
		try {
			YoutubeItem signalItem = YoutubeItem.fromPageUrl(pageUrl, "", 0L);
			YoutubeItem loadedItem = YoutubeItem.fromPageUrl(web.getUrl(), "", 0L);
			return signalItem.videoId().equals(loadedItem.videoId());
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	boolean isCurrentEngine() {
		return cb.getEngine() == this;
	}

	boolean isCurrentVideo(String videoId) {
		if ((videoId == null) || !(current instanceof Current active) ||
				(active.descriptor == null)) return false;
		return videoId.equals(active.descriptor.videoId());
	}

	boolean ownsPlayback(String videoId) {
		return isCurrentEngine() && isCurrentVideo(videoId);
	}

	YoutubePlaybackSession.Snapshot playbackSnapshot(String videoId) {
		if (!ownsPlayback(videoId)) return null;
		return playbackSession.currentFor(videoId);
	}

	boolean belongsTo(YoutubeAddon addon) {
		return (addon != null) && (web.getAddon() == addon);
	}

	boolean isPlaybackActive() {
		return cb.isPlaying();
	}

	@Nullable
	PlayableItem getExternalPlaybackOwner() {
		return externalPlaybackOwner;
	}

	boolean transferTo(YoutubeMediaEngine replacement) {
		if ((replacement == null) || (replacement == this) || !isCurrentEngine()) return false;
		boolean wasPlaying = cb.isPlaying();
		Current active = (current instanceof Current value) ? value : null;
		YoutubeItem descriptor = (active == null) ? null : active.descriptor;
		boolean ended = current == end;
		YoutubeMediaEngine source = (transferSource == null) ? this : transferSource;
		FutureSupplier<Long> sourcePosition = (descriptor == null) ? completed(0L) :
				source.getPosition().main();
		if (!cb.replaceEngine(this, replacement)) return false;

		if (descriptor != null) {
			replacement.current = replacement.new Current(descriptor);
			replacement.externalPlaybackOwner = externalPlaybackOwner;
			replacement.externalPlaybackVideoId = externalPlaybackVideoId;
			playbackOwner.transferTo(replacement.playbackOwner);
			replacement.playbackSessionSnapshot = replacement.playbackSession.begin(
					descriptor, System.currentTimeMillis());
			replacement.adPlaybackGeneration = replacement.adController
					.beginPlayback(replacement.current.getId()).generation();
			replacement.transferSource = source;
			replacement.transferVideoId = descriptor.videoId();
			replacement.transferPosition = Math.max(0L, cb.getPlaybackState().getPosition());
			replacement.transferPositionReady = sourcePosition.isDone();
			if (replacement.transferPositionReady)
				replacement.transferPosition = Math.max(0L, sourcePosition.get(() -> replacement.transferPosition));
			float playbackSpeed = cb.getPlaybackState().getPlaybackSpeed();
			replacement.transferSpeed = (playbackSpeed > 0f) ? playbackSpeed : 1f;
			replacement.transferPlayRequested = wasPlaying;
			if (!replacement.transferPositionReady) {
				sourcePosition.onCompletion((position, error) -> {
					if ((replacement.transferSource != source) ||
							!descriptor.videoId().equals(replacement.transferVideoId)) return;
					if ((error == null) && (position != null))
						replacement.transferPosition = Math.max(0L, position);
					replacement.transferPositionReady = true;
					replacement.tryCompletePendingTransfer();
				});
			}
		} else if (ended) {
			replacement.current = replacement.end;
		}
		replacement.qualityUrl = qualityUrl;
		if (wasPlaying) replacement.playbackIntentGate.armExplicitPlayback();

		transferSource = null;
		externalPlaybackOwner = null;
		externalPlaybackVideoId = "";
		if (source != this || descriptor == null) discardBridgeState();
		source.playbackIntentGate.reset();
		source.fullScreenCoordinator.cancelPlayback();
		return true;
	}

	private void completePendingTransfer() {
		transferTargetReady = true;
		tryCompletePendingTransfer();
	}

	private void tryCompletePendingTransfer() {
		YoutubeMediaEngine source = transferSource;
		if ((source == null) || !isCurrentEngine() || !transferPositionReady ||
				!transferTargetReady) return;
		transferSource = null;

		long position = transferPosition;
		if (!(current instanceof Current active) || (active.descriptor == null) ||
				!transferVideoId.equals(active.descriptor.videoId())) position = 0L;
		boolean play = transferPlayRequested && cb.isPlaying();
		float speed = transferSpeed;
		resetTransferState();

		source.finishTransferredOut(true);
		if (position > 0L) web.setPosition(position);
		if (speed > 0f && speed != 1f) web.setSpeed(speed);
		if (current instanceof Current active && active.descriptor != null)
			web.onYoutubePlaybackItemChanged(active.descriptor);
		if (play) web.play();
		else web.pause();
		cb.onEngineMetadataChanged(this);
	}

	private void finishPendingTransferSource() {
		YoutubeMediaEngine source = transferSource;
		if (source == null) return;
		transferSource = null;
		resetTransferState();
		source.finishTransferredOut(false);
	}

	private void finishTransferredOut(boolean handoff) {
		if (!webDestroyed) {
			if (handoff) web.pause();
			else web.stop();
		}
		discardBridgeState();
	}

	void onWebViewDestroyed() {
		if (webDestroyed) return;
		recordPlayback(PlaybackEvent.OWNERSHIP_LOST, playbackSessionSnapshot,
				false, false, false, false, false, 0L);
		clearFullscreenTapPauseGuard();
		webDestroyed = true;
		if (isCurrentEngine()) cb.onStop();
	}

	private void discardBridgeState() {
		clearFullscreenTapPauseGuard();
		endAdPlayback();
		current = null;
		audibleStartPending = false;
		qualityUrl = null;
		playbackIntentGate.reset();
		playbackSession.invalidate();
		recordPlayback(PlaybackEvent.SESSION_INVALIDATED, playbackSessionSnapshot,
				false, false, false, false, false, 0L);
		playbackSessionSnapshot = null;
		if (!webDestroyed) web.clearPlaybackGeneration();
		web.onYoutubePlaybackOwnershipLost();
		fullScreenCoordinator.cancelPlayback();
		clearExternalPlaybackOwner();
		playbackOwner.clear();
	}

	private void resetTransferState() {
		transferVideoId = "";
		transferPosition = 0L;
		transferSpeed = 1f;
		transferPlayRequested = false;
		transferPositionReady = false;
		transferTargetReady = false;
	}

	boolean isYoutubeActive(MainActivityDelegate a) {
		return (a.getActiveFragment() instanceof YoutubeFragment youtube) &&
				youtube.getWebView() == web;
	}

	@Nullable
	private static YoutubeDescriptorItem descriptorItem(PlayableItem source) {
		if (source instanceof YoutubeDescriptorItem item) return item;
		if (source instanceof ExternalPlaybackDelegateItem external) {
			PlayableItem delegate = PlayableItemResolver.unwrap(
					external.getExternalPlaybackDelegate());
			if (delegate instanceof YoutubeDescriptorItem item) return item;
		}
		return null;
	}

	private void clearExternalPlaybackOwner() {
		externalPlaybackOwner = null;
		externalPlaybackVideoId = "";
	}

	private String setCurrent(String url) {
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse(url, web.getUrl());
		String pageUrl = signal.pageUrl();
		String mediaUrl = signal.mediaUrl();
		if (pageUrl.isEmpty()) pageUrl = mediaUrl;
		if (pageUrl.isEmpty()) pageUrl = "https://m.youtube.com";
		if (mediaUrl.isEmpty()) mediaUrl = pageUrl;
		if (mediaUrl.startsWith("blob:")) mediaUrl = mediaUrl.substring(5);

		boolean titleChanged = playbackMetadata.apply(
				new YoutubePlaybackMetadata.Signal(pageUrl, mediaUrl, signal.title()));
		me.aap.fermata.addon.web.yt.YoutubeItem descriptor = null;
		try {
			descriptor = me.aap.fermata.addon.web.yt.YoutubeItem.fromPageUrl(
					pageUrl, playbackMetadata.getTitle(), System.currentTimeMillis());
		} catch (IllegalArgumentException ignored) {
			// Keep the legacy transient item for non-video pages and malformed navigation signals.
		}

		boolean newItem = !(current instanceof Current c) ||
				!c.matches(descriptor, pageUrl);
		playbackOwner.retain((descriptor == null) ? "" : descriptor.videoId());
		if (newItem) {
			if ((descriptor == null) ||
					!descriptor.videoId().equals(externalPlaybackVideoId)) {
				clearExternalPlaybackOwner();
			}
			current = (descriptor == null) ? new Current(pageUrl) : new Current(descriptor);
			if (descriptor != null) {
				playbackSessionSnapshot = playbackSession.begin(descriptor,
						System.currentTimeMillis());
				recordPlayback(PlaybackEvent.SESSION_STARTED, playbackSessionSnapshot,
						false, false, false, false, false, 0L);
				web.getAddon().rememberYoutubeItem(descriptor);
				web.onYoutubePlaybackItemChanged(descriptor);
			} else {
				playbackSession.invalidate();
				playbackSessionSnapshot = null;
			}
			cancelAdRetry();
			adPlaybackGeneration = adController.beginPlayback(current.getId()).generation();
		}
		else if (titleChanged) ((Current) current).updateTitle(playbackMetadata.getTitle());
		if (playbackSessionSnapshot != null)
			web.setPlaybackGeneration(playbackSessionSnapshot.generation());
		if (newItem && isCurrentEngine()) cb.onEngineMetadataChanged(this);
		return mediaUrl;
	}

	private boolean matchesCurrentSignal(String data) {
		if (!(current instanceof Current active) ||
				!YoutubePlaybackMetadata.isStructuredSignal(data) || (playbackSessionSnapshot == null) ||
				!playbackSession.isCurrent(playbackSessionSnapshot)) return false;
		long signalGeneration = YoutubePlaybackMetadata.playbackGeneration(data);
		if ((signalGeneration <= 0L) ||
				(signalGeneration != playbackSessionSnapshot.generation())) return false;
		YoutubePlaybackMetadata.Signal signal = YoutubePlaybackMetadata.parse(data, web.getUrl());
		if (!YoutubePlaybackMetadata.hasTrustedPlaybackIdentity(data, signal)) return false;
		if (!matchesLoadedPage(signal.pageUrl())) return false;
		me.aap.fermata.addon.web.yt.YoutubeItem descriptor = null;
		try {
			descriptor = me.aap.fermata.addon.web.yt.YoutubeItem.fromPageUrl(
					signal.pageUrl(), signal.title(), System.currentTimeMillis());
		} catch (IllegalArgumentException ignored) {
		}
		return (descriptor != null) && active.matches(descriptor, signal.pageUrl()) &&
				playbackSessionSnapshot.item().videoId().equals(descriptor.videoId());
	}

	private boolean isFullscreenTapEligible() {
		if (webDestroyed || !isCurrentEngine() || !web.isAttachedToWindow()) return false;
		try {
			MainActivityDelegate activity = MainActivityDelegate.get(web.getContext());
			if (activity.isVideoMode() ||
					!(activity.getActiveFragment() instanceof YoutubeFragment youtube) ||
					(youtube.getWebView() != web)) return false;
			FermataChromeClient chrome = web.getWebChromeClient();
			return ((chrome == null) || !chrome.isFullScreen()) &&
					isYoutubeItem(activity.getMediaServiceBinder().getCurrentItem());
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private boolean consumeFullscreenTapPauseGuard(String signal) {
		long now = SystemClock.uptimeMillis();
		if ((fullscreenTapPauseGuardUntil == 0L) || (now > fullscreenTapPauseGuardUntil)) {
			clearFullscreenTapPauseGuard();
			return false;
		}
		YoutubePlaybackMetadata.Signal parsed = YoutubePlaybackMetadata.parse(signal, web.getUrl());
		if ((parsed.generation() != fullscreenTapPauseGeneration) ||
				!parsed.videoId().equals(fullscreenTapPauseVideoId)) return false;
		clearFullscreenTapPauseGuard();
		return true;
	}

	private void clearFullscreenTapPauseGuard() {
		fullscreenTapPauseGuardUntil = 0L;
		fullscreenTapPauseGeneration = 0L;
		fullscreenTapPauseVideoId = "";
	}

	private void updatePlaybackSession(YoutubeItem descriptor) {
		YoutubePlaybackSession.Snapshot snapshot = playbackSessionSnapshot;
		if ((snapshot != null) && playbackSession.update(snapshot, descriptor))
			playbackSessionSnapshot = playbackSession.current();
	}

	@Override
	public boolean hasVideoMenu() {
		return true;
	}

	@Override
	public void contributeToMenu(OverlayMenu.Builder b) {
		Context ctx = dynCtx(web.getContext());
		Resources r = ctx.getResources();
		SplitCompat.install(ctx);
		b.addItem(R.id.video_quality,
				ResourcesCompat.getDrawable(r, R.drawable.video_quality, ctx.getTheme()),
				r.getString(R.string.video_quality)).setFutureSubmenu(
				menu -> YoutubeQualityMenu.populate(web, menu, this));
		b.addItem(me.aap.fermata.R.id.video_scaling,
				ResourcesCompat.getDrawable(r, R.drawable.video_scaling, ctx.getTheme()),
				r.getString(me.aap.fermata.R.string.video_scaling)).setSubmenu(this::videoScalingMenu);
	}

	private void videoScalingMenu(OverlayMenu.Builder b) {
		VideoScale scale = web.getAddon().getScale();
		b.addItem(me.aap.fermata.R.id.video_scaling_best, null, me.aap.fermata.R.string.video_scaling_best)
				.setChecked(scale == VideoScale.CONTAIN, true);
		b.addItem(me.aap.fermata.R.id.video_scaling_fill, null, me.aap.fermata.R.string.video_scaling_fill)
				.setChecked(scale == VideoScale.FILL, true);
		b.addItem(R.id.video_scaling_fill_proportional, null, R.string.video_scaling_fill_proportional)
				.setChecked(scale == VideoScale.COVER, true);
		b.addItem(me.aap.fermata.R.id.video_scaling_orig, null, me.aap.fermata.R.string.video_scaling_orig)
				.setChecked(scale == VideoScale.NONE, true);
		b.setSelectionHandler(this);
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == me.aap.fermata.R.id.video_scaling_best) {
			web.setScale(VideoScale.CONTAIN);
			return true;
		} else if (itemId == me.aap.fermata.R.id.video_scaling_fill) {
			web.setScale(VideoScale.FILL);
			return true;
		} else if (itemId == R.id.video_scaling_fill_proportional) {
			web.setScale(VideoScale.COVER);
			return true;
		} else if (itemId == me.aap.fermata.R.id.video_scaling_orig) {
			web.setScale(VideoScale.NONE);
			return true;
		} else if (YoutubeQualityMenu.select(web, item)) return true;
		return false;
	}

	@Override
	public boolean isSplitModeSupported() {
		return false;
	}

	static boolean isYoutubeItem(MediaLib.Item i) {
		if (i instanceof YoutubePlayableItem) return true;
		if (i instanceof ExternalPlaybackDelegateItem external) {
			PlayableItem delegate = external.getExternalPlaybackDelegate();
			if (delegate != i) return isYoutubeItem(delegate);
		}
		if (i == null) return false;
		String id = i.getId();
		if (i instanceof PlayableItem playable) id = playable.getOrigId();
		return id.startsWith("youtube:video:");
	}

	static class YoutubePlayableItem extends ExtPlayable {
		public YoutubePlayableItem(String id, @NonNull BrowsableItem parent, @NonNull VirtualResource resource) {
			super(id, parent, resource);
		}

		@Override
		public boolean isSeekable() {
			return true;
		}

		@Override
		public boolean isVideo() {
			return true;
		}

		@Override
		public int getVideoEnginePref() {
			return MEDIA_ENG_YT;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			return obj == this;
		}

		@Override
		protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
			return null;
		}
	}

	private static class TransportItem extends YoutubePlayableItem {
		TransportItem(String id, @NonNull BrowsableItem parent, @NonNull VirtualResource resource) {
			super(id, parent, resource);
		}

		@Override
		public boolean isRecentEligible() {
			return false;
		}

		@Override
		public boolean isPlaybackTransportCommand() {
			return true;
		}
	}

	private final class Current extends YoutubePlayableItem implements YoutubeDescriptorItem {
		private final String pageUrl;
		private me.aap.fermata.addon.web.yt.YoutubeItem descriptor;

		public Current(String url) {
			super(CURRENT_ID, mediaRoot, GenericFileSystem.getInstance().create(url));
			pageUrl = url;
		}

		Current(me.aap.fermata.addon.web.yt.YoutubeItem descriptor) {
			super(descriptor.stableId(), mediaRoot,
					GenericFileSystem.getInstance().create(descriptor.pageUrl()));
			this.descriptor = descriptor;
			pageUrl = descriptor.pageUrl();
		}

		@Override
		public me.aap.fermata.addon.web.yt.YoutubeItem getYoutubeDescriptor() {
			return descriptor;
		}

		boolean matches(me.aap.fermata.addon.web.yt.YoutubeItem next, String fallbackUrl) {
			if (descriptor != null) return (next != null) && descriptor.videoId().equals(next.videoId());
			return next == null && pageUrl.equals(fallbackUrl);
		}

		void updateTitle(String title) {
			if ((descriptor == null) || !isCurrentSession(playbackSessionSnapshot)) return;
			String normalized = YoutubePlaybackMetadata.normalizeTitle(title);
			if (!normalized.equals(descriptor.title())) {
				descriptor = descriptor.withTitle(normalized);
				updatePlaybackSession(descriptor);
				web.getAddon().updateYoutubeItem(descriptor);
				invalidateMetadata();
				if ((current == this) && isCurrentEngine())
					cb.onEngineMetadataChanged(YoutubeMediaEngine.this);
			}
		}

		@NonNull
		@Override
		public String getName() {
			String title = (descriptor == null) ? playbackMetadata.getTitle() : descriptor.title();
			return title.isEmpty() ? web.getContext().getString(
					me.aap.fermata.R.string.addon_name_youtube) : title;
		}

		@Nullable
		@Override
		public MediaEngine getMediaEngine(@Nullable MediaEngine current,
				MediaEngine.Listener listener) {
			return YoutubeMediaEngine.this;
		}

		@NonNull
		@Override
		protected FutureSupplier<MediaMetadataCompat> loadMeta() {
			YoutubeItem expectedDescriptor = descriptor;
			YoutubePlaybackSession.Snapshot expectedSession = playbackSessionSnapshot;
			String title = (expectedDescriptor == null) ? playbackMetadata.getTitle() :
					expectedDescriptor.title();
			FutureSupplier<String> getTitle = title.isEmpty() ? web.getVideoTitle().map(value -> {
				if (!isCurrentSession(expectedSession)) return title;
				playbackMetadata.apply(new YoutubePlaybackMetadata.Signal(pageUrl, "", value));
				updateTitle(playbackMetadata.getTitle());
				return getName();
			}) : completed(title);
			long fallbackDuration = (expectedDescriptor == null) ? 0L :
					expectedDescriptor.durationMillis();
			return web.getContentDuration().then(contentDuration -> getTitle.map(resolvedTitle -> {
				long dur = (contentDuration > 0L) ? contentDuration : fallbackDuration;
				if ((expectedDescriptor != null) && isCurrentSession(expectedSession)) {
					descriptor = descriptor.withPlaybackMetadata(resolvedTitle,
							descriptor.thumbnailUrl(), dur);
					updatePlaybackSession(descriptor);
					web.getAddon().updateYoutubeItem(descriptor);
				}
				MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
				b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, resolvedTitle);
				b.putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
				return b.build();
			}));
		}

		private boolean isCurrentSession(YoutubePlaybackSession.Snapshot expected) {
			return (descriptor != null) && (expected != null) && (current == this) &&
				isCurrentEngine() && playbackSession.isCurrent(expected) &&
				expected.item().videoId().equals(descriptor.videoId());
		}

		@Override
		protected boolean isMediaDataValid(FutureSupplier<MediaMetadataCompat> data) {
			if ((data == null) || !data.isDone()) return data != null;
			MediaMetadataCompat metadata = data.peek(() -> null);
			return (metadata != null) && getName().equals(
					metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
		}

		private void invalidateMetadata() {
			reset();
		}

		@NonNull
		@Override
		public FutureSupplier<PlayableItem> getPrevPlayable() {
			return completed(prev);
		}

		@NonNull
		@Override
		public FutureSupplier<PlayableItem> getNextPlayable() {
			return completed(next);
		}
	}
}
