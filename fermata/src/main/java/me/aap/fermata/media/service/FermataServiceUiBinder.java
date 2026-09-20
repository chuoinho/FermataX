package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_END_TIME;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_START_TIME;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.auto.AutomotiveNavigationController;
import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;
import me.aap.fermata.auto.OpenOnCarKind;
import me.aap.fermata.auto.OpenOnCarMediaRouting;
import me.aap.fermata.auto.OpenOnCarMediaRouting.Admission;
import me.aap.fermata.auto.OpenOnCarMediaRouting.Cause;
import me.aap.fermata.auto.OpenOnCarMediaRouting.Selection;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.ProgressOwnership.LastPlayedLease;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;
import me.aap.fermata.ui.policy.RuntimeSessionCoordinator;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.log.Log;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class FermataServiceUiBinder extends BasicEventBroadcaster<FermataServiceUiBinder.Listener>
		implements OnSeekBarChangeListener {
	private final MediaSessionCallback sessionCallback;
	private final MediaControllerCompat mediaController;
	private final RuntimeSessionCoordinator runtimeSessions = new RuntimeSessionCoordinator();
	@Nullable
	private MediaControllerCallback callback;
	@Nullable
	private RuntimeSessionCoordinator.Token presentationToken;
	private PlaybackSnapshot deliveredSnapshot;
	@Nullable
	private PlaybackTimelineSnapshot playbackTimelineSnapshot;
	private boolean bound;
	@Nullable
	private View playPauseButton;
	@Nullable
	private View prevButton;
	@Nullable
	private View nextButton;
	@Nullable
	private View rwButton;
	@Nullable
	private View ffButton;
	@Nullable
	private SeekBar progressBar;
	@Nullable
	private TextView progressTime;
	@Nullable
	private TextView progressTotal;
	@Nullable
	private View liveBadge;
	@Nullable
	private View controlPanel;
	private long playPauseTime;
	private final OpenOnCarMediaRouting mediaRouting =
			new OpenOnCarMediaRouting(AutomotiveNavigationController.get());

	FermataServiceUiBinder(FermataMediaServiceConnection c) {
		sessionCallback = c.getMediaSessionCallback();
		MediaSessionCompat session = sessionCallback.getSession();
		mediaController = new MediaControllerCompat(App.get(),
				session.getSessionToken());
	}

	@NonNull
	public MediaSessionCallback getMediaSessionCallback() {
		return sessionCallback;
	}

	@NonNull
	public MediaLib getLib() {
		return getMediaSessionCallback().getMediaLib();
	}

	public boolean isPlaying() {
		PlaybackStateCompat st = mediaController.getPlaybackState();
		return (st != null) && (st.getState() == STATE_PLAYING || st.getState() == STATE_BUFFERING);
	}

	@NonNull
	public PlaybackSnapshot getPlaybackSnapshot() {
		return sessionCallback.getPlaybackSnapshot();
	}

	public void play() {
		mediaController.getTransportControls().play();
	}

	public void pause() {
		mediaController.getTransportControls().pause();
	}

	public void seekTo(long positionMillis) {
		mediaController.getTransportControls().seekTo(Math.max(0L, positionMillis));
	}

	@Nullable
	public ControlSeekToken captureControlSeekToken() {
		MediaControllerCallback current = callback;
		RuntimeSessionCoordinator.Token presentation = presentationToken;
		PlaybackSnapshot snapshot = getPlaybackSnapshot();
		PlaybackTimelineSnapshot timeline = playbackTimelineSnapshot;
		PlayableItem item = snapshot.getItem();
		if (!validControlSeekState(current, presentation, snapshot, timeline, item)) return null;
		LastPlayedLease playback = sessionCallback.captureLastPlayed(item);
		return sessionCallback.isStillLastPlayedOwner(playback) &&
				sessionCallback.isCurrentEngineSource(playback.item()) ?
				new ControlSeekToken(this, presentation, playback) : null;
	}

	public boolean seekToIfCurrent(@Nullable ControlSeekToken token, long positionMillis) {
		if ((token == null) || (token.binder != this) || (token.presentation != presentationToken) ||
				!runtimeSessions.isCurrent(token.presentation) ||
				!sessionCallback.isStillLastPlayedOwner(token.playback) ||
				!sessionCallback.isCurrentEngineSource(token.playback.item())) return false;
		PlaybackSnapshot snapshot = getPlaybackSnapshot();
		PlaybackTimelineSnapshot timeline = playbackTimelineSnapshot;
		PlayableItem item = snapshot.getItem();
		if (!validControlSeekState(callback, token.presentation, snapshot, timeline, item) ||
				(PlayableItemResolver.unwrap(item) != token.playback.item())) return false;
		mediaController.getTransportControls().seekTo(
				Math.min(Math.max(0L, positionMillis), timeline.durationMillis()));
		return true;
	}

	private boolean validControlSeekState(@Nullable MediaControllerCallback current,
			@Nullable RuntimeSessionCoordinator.Token presentation, PlaybackSnapshot snapshot,
			@Nullable PlaybackTimelineSnapshot timeline, @Nullable PlayableItem item) {
		return (current != null) && (presentation != null) && isPresentationBound(current) &&
				(item != null) && (timeline != null) &&
				(PlayableItemResolver.unwrap(timeline.item()) == PlayableItemResolver.unwrap(item)) &&
				(timeline.presentationGeneration() == presentation.generation()) &&
				(timeline.mode() == PlaybackTimelinePolicy.Mode.SEEKABLE) &&
				(timeline.durationMillis() > 0L) &&
				((snapshot.getState().getActions() & ACTION_SEEK_TO) != 0L);
	}

	public static final class ControlSeekToken {
		private final FermataServiceUiBinder binder;
		private final RuntimeSessionCoordinator.Token presentation;
		private final LastPlayedLease playback;

		private ControlSeekToken(FermataServiceUiBinder binder,
				RuntimeSessionCoordinator.Token presentation, LastPlayedLease playback) {
			this.binder = binder;
			this.presentation = presentation;
			this.playback = playback;
		}
	}

	@Nullable
	public PlayableItem getCurrentItem() {
		return (deliveredSnapshot == null) ? null : deliveredSnapshot.getItem();
	}

	@Nullable
	public MediaEngine getCurrentEngine() {
		return getMediaSessionCallback().getEngine();
	}

	public boolean playItem(PlayableItem i) {
		return playItem(i, -1);
	}

	@Nullable
	public PlaybackTimelineSnapshot getPlaybackTimelineSnapshot() {
		return playbackTimelineSnapshot;
	}

	public boolean playItem(PlayableItem i, long pos) {
		Selection selection = captureUserSelection();
		if (selection.forwardToCar()) {
			routeUserSelection(selection, i, pos, admission -> {
				throw new IllegalStateException("Car request reached phone executor");
			});
			return false;
		}
		return playRoutedItem(i, pos, admission(selection));
	}

	public Selection captureUserSelection() {
		RuntimeHostMode source = presentationToken == null ? RuntimeHostMode.PHONE : presentationToken.mode();
		RuntimeHostMode owner = sessionCallback.getVideoOutputCoordinator().getHost();
		return mediaRouting.capture(source, Cause.USER_SELECTION, owner == null ? source : owner);
	}

	public FutureSupplier<OpenResult> routeUserSelection(Selection selection, PlayableItem item,
			long position, java.util.function.Function<Admission, FutureSupplier<OpenResult>> prepareLocal) {
		// An active Cast/custom provider is never disconnected or repurposed by this switch.
		if (selection.forwardToCar() && sessionCallback.hasCustomEngineProvider())
			return me.aap.utils.async.Completed.completed(OpenResult.NOT_READY);
		return mediaRouting.dispatch(selection, OpenOnCarKind.MEDIA_ITEM, 0,
				new OpenOnCarMediaRouting.MediaItem(item, position), () -> prepareLocal.apply(admission(selection)));
	}

	private Admission admission(Selection selection) {
		return new Admission(selection.target(), () -> mediaRouting.isCurrent(selection));
	}

	/** The web module supplies its immutable descriptor, not a page/media URL. */
	public FutureSupplier<OpenResult> dispatchYoutubeSelection(Selection selection, Object descriptor,
			java.util.function.BooleanSupplier sourceCurrent) {
		if (!selection.forwardToCar() || !sourceCurrent.getAsBoolean() || sessionCallback.hasCustomEngineProvider())
			return me.aap.utils.async.Completed.completed(OpenResult.NOT_READY);
		return mediaRouting.dispatch(selection, OpenOnCarKind.YOUTUBE_VIDEO, R.id.youtube_fragment,
				descriptor, () -> me.aap.utils.async.Completed.completed(OpenResult.NOT_READY), sourceCurrent);
	}

	/** Already routed executor: transport and car receivers never re-read the switch. */
	public static OpenResult dispatchRoutedPlayback(Admission admission,
			java.util.function.BooleanSupplier customProvider,
			java.util.function.BooleanSupplier dispatch) {
		if (!admission.isCurrent()) return OpenResult.CANCELLED;
		if (!admission.canAttach() || (admission.target() == RuntimeHostMode.AA_PROJECTION &&
				customProvider.getAsBoolean())) return OpenResult.NOT_READY;
		boolean started = dispatch.getAsBoolean();
		if (!admission.isCurrent()) return OpenResult.CANCELLED;
		return started ? OpenResult.LOAD_DISPATCHED : OpenResult.NOT_READY;
	}

	public boolean playRoutedItem(PlayableItem i, long pos, Admission admission) {
		if (!admission.isCurrent()) return false;
		boolean sameItem = i.equals(getCurrentItem());
		boolean sameHost = admission.target() == sessionCallback.getVideoOutputCoordinator().getHost();
		if (!shouldCreatePlaybackRequest(sameItem, pos, sameHost)) {
			if (!admission.commit()) return false;
			if (sessionCallback.getPlaybackState().getState() == PlaybackStateCompat.STATE_PAUSED) {
				sessionCallback.onPlay();
			}
			return false;
		} else {
			if (!sessionCallback.admitPlaybackHost(admission)) return false;
			sessionCallback.playItem(i, pos);
			return true;
		}
	}

	static boolean shouldCreatePlaybackRequest(boolean sameItem, long position) {
		return !sameItem || (position > 0);
	}

	static boolean shouldCreatePlaybackRequest(boolean sameItem, long position, boolean sameHost) {
		return !sameHost || shouldCreatePlaybackRequest(sameItem, position);
	}

	public void stop() {
		mediaController.getTransportControls().stop();
	}

	/** Common transport path used by Playerbar, SmartTop and projected controls. */
	public void togglePlayback(PlayableItem item) {
		PlayableItem current = getCurrentItem();
		if ((current != null) && DashboardPlaybackIdentity.same(current, item) && isPlaying()) {
			mediaController.getTransportControls().pause();
		} else {
			RuntimeHostMode owner = sessionCallback.getVideoOutputCoordinator().getHost();
			playRoutedItem(item, -1, new Admission(owner == null ? RuntimeHostMode.PHONE : owner,
					() -> true));
		}
	}

	public void skipToPrevious() {
		mediaController.getTransportControls().skipToPrevious();
	}

	public void skipToNext() {
		mediaController.getTransportControls().skipToNext();
	}

	public void bindPlayPauseButton(View v) {
		playPauseButton = v;
		v.setOnClickListener(b -> onPlayPauseButtonClick());
		v.setOnLongClickListener(this::onPlayPauseButtonLongClick);
	}

	public void onPlayPauseButtonClick() {
		var time = SystemClock.uptimeMillis();
		if (getMediaSessionCallback().getPlaybackState().getState() ==
				PlaybackStateCompat.STATE_CONNECTING) {
			mediaController.getTransportControls().stop();
			return;
		}
		if ((time - playPauseTime) < 300) {
			mediaController.getTransportControls().stop();
		} else {
			playPauseTime = time;
			if (isPlaying()) mediaController.getTransportControls().pause();
			else mediaController.getTransportControls().play();
		}
	}

	private boolean onPlayPauseButtonLongClick(View v) {
		if (getMediaSessionCallback().getPlaybackControlPrefs().getPlayPauseStopPref()) {
			mediaController.getTransportControls().stop();
		} else if (isPlaying()) {
			mediaController.getTransportControls().pause();
		} else {
			mediaController.getTransportControls().play();
		}
		return true;
	}

	public void bindPrevButton(View v) {
		prevButton = v;
		v.setOnClickListener(this::onPrevNextButtonClick);
		v.setOnLongClickListener(this::onPrevNextButtonLongClick);
	}

	public void bindNextButton(View v) {
		nextButton = v;
		v.setOnClickListener(this::onPrevNextButtonClick);
		v.setOnLongClickListener(this::onPrevNextButtonLongClick);
	}

	public void bindRwButton(View v) {
		rwButton = v;
		v.setOnClickListener(this::onRwFfButtonClick);
		v.setOnLongClickListener(this::onRwFfButtonLongClick);
	}

	public void bindFfButton(View v) {
		ffButton = v;
		v.setOnClickListener(this::onRwFfButtonClick);
		v.setOnLongClickListener(this::onRwFfButtonLongClick);
	}

	public void bindLiveBadge(View v) {
		liveBadge = v;
	}

	private void onPrevNextButtonClick(View v) {
		onPrevNextButtonClick(v == nextButton);
	}

	public void onPrevNextButtonClick(boolean next) {
		if (next) skipToNext();
		else skipToPrevious();
	}

	private boolean onPrevNextButtonLongClick(View v) {
		onPrevNextButtonLongClick(v == nextButton);
		return true;
	}

	public void onPrevNextButtonLongClick(boolean next) {
		MediaSessionCallback cb = getMediaSessionCallback();
		PlaybackControlPrefs pp = cb.getPlaybackControlPrefs();
		cb.rewindFastForward(next, pp.getPrevNextLongTimePref(),
				pp.getPrevNextLongTimeUnitPref(), 1);
	}

	public void onPrevNextFolderClick(boolean next) {
		PlayableItem i = sessionCallback.getCurrentItem();
		if (i == null) return;
		MediaLib.BrowsableItem p = i.getParent();
		FutureSupplier<PlayableItem> f = next ? p.getNextPlayable() : p.getPrevPlayable();
		f.onSuccess(pi -> {
			if (pi != null) sessionCallback.playItem(pi, 0);
		});
	}

	private void onRwFfButtonClick(View v) {
		onRwFfButtonClick(v == ffButton);
	}

	public void onRwFfButtonClick(boolean ff) {
		if (ff) {
			mediaController.getTransportControls().fastForward();
		} else {
			mediaController.getTransportControls().rewind();
		}
	}

	private boolean onRwFfButtonLongClick(View v) {
		onRwFfButtonLongClick(v == ffButton);
		return true;
	}

	public void onRwFfButtonLongClick(boolean ff) {
		MediaSessionCallback cb = getMediaSessionCallback();
		PlaybackControlPrefs pp = cb.getPlaybackControlPrefs();
		cb.rewindFastForward(ff, pp.getRwFfLongTimePref(), pp.getRwFfLongTimeUnitPref(), 1);
	}

	public void bindProgressBar(SeekBar progressBar) {
		this.progressBar = progressBar;
		progressBar.setOnSeekBarChangeListener(this);
	}

	public void bindProgressTime(TextView progressTime) {
		this.progressTime = progressTime;
		progressTime.setVisibility(INVISIBLE);
	}

	public void bindProgressTotal(TextView progressTotal) {
		this.progressTotal = progressTotal;
		progressTotal.setVisibility(INVISIBLE);
	}

	public void bindControlPanel(View controlPanel) {
		this.controlPanel = controlPanel;
	}

	public void bound(RuntimeHostMode hostMode) {
		if (bound) return;
		RuntimeSessionCoordinator.Token token = runtimeSessions.attach(this, hostMode);
		MediaControllerCallback callback = new MediaControllerCallback(token);
		presentationToken = token;
		this.callback = callback;
		bound = true;
		mediaController.registerCallback(callback);
		callback.onPlaybackStateChanged(mediaController.getPlaybackState());
		Log.d("UI bound");
	}

	public void unbind() {
		mediaRouting.invalidate();
		if (!bound) return;
		MediaControllerCallback callback = this.callback;
		RuntimeSessionCoordinator.Token token = presentationToken;
		bound = false;
		presentationToken = null;
		this.callback = null;
		runtimeSessions.detach(token);
		if (callback != null) {
			callback.stopProgressUpdate();
			mediaController.unregisterCallback(callback);
		}
		deliveredSnapshot = null;
		playbackTimelineSnapshot = null;
		if (progressBar != null) progressBar.setOnSeekBarChangeListener(null);
		liveBadge = null;
		unbindButtons(playPauseButton, prevButton, nextButton, rwButton, ffButton);
		playPauseButton = prevButton = nextButton = rwButton = ffButton = null;
		progressTime = progressTotal = null;
		progressBar = null;
		controlPanel = null;
		Log.d("UI unbound");
	}

	/** True only while this binder owns the active presentation callback. */
	public boolean isControlAvailable() {
		MediaControllerCallback current = callback;
		return (current != null) && isPresentationBound(current);
	}

	private boolean isPresentationBound(MediaControllerCallback candidate) {
		return isControlAvailable(bound, callback, candidate,
				runtimeSessions.isCurrent(candidate.ownerToken));
	}

	static boolean isControlAvailable(boolean bound, @Nullable Object activeCallback,
			@Nullable Object candidateCallback, boolean currentSession) {
		return ownsPresentationLease(bound, activeCallback, candidateCallback, currentSession);
	}

	static boolean ownsPresentationLease(boolean bound, @Nullable Object activeCallback,
			@Nullable Object candidateCallback, boolean currentSession) {
		return bound && (candidateCallback != null) && (activeCallback == candidateCallback) &&
				currentSession;
	}

	private void unbindButtons(View... buttons) {
		for (View b : buttons) {
			if (b == null) continue;
			b.setOnClickListener(null);
			b.setOnLongClickListener(null);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			MediaControllerCallback callback = this.callback;
			if (callback != null) callback.setProgressTime(progress);
			mediaController.getTransportControls().seekTo(progress * 1000L);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		MediaControllerCallback callback = this.callback;
		if (callback != null) callback.pauseProgressUpdate(true);
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		MediaControllerCallback callback = this.callback;
		if (callback != null) callback.pauseProgressUpdate(false);
	}

	private final class MediaControllerCallback extends MediaControllerCompat.Callback {
		private final RuntimeSessionCoordinator.Token ownerToken;
		private final Handler handler = FermataApplication.get().getHandler();
		private final StringBuilder timeBuilder = new StringBuilder(10);
		private Object progressUpdateStamp;
		boolean pauseProgressUpdate;
		boolean updateDuration;
		FutureSupplier<Long> duration;

		MediaControllerCallback(RuntimeSessionCoordinator.Token ownerToken) {
			this.ownerToken = ownerToken;
		}

		@Override
		public void onPlaybackStateChanged(PlaybackStateCompat state) {
			if ((state == null) || !isCurrent()) return;
			fireBroadcastEvent(l -> l.onPlaybackStateChanged(state));

			switch (state.getState()) {
				case PlaybackStateCompat.STATE_PAUSED:
				case STATE_PLAYING:
					playPause(state.getState());
					break;
				case PlaybackStateCompat.STATE_ERROR:
					String fallback = getLib().getContext().getString(R.string.err_failed_to_play,
							sessionCallback.getCurrentItem());
					String err = normalizePlaybackError(state.getErrorMessage(), fallback);
					fireBroadcastEvent(l -> l.onPlaybackError(err));
					PlayableItem failedItem = sessionCallback.getCurrentItem();
					if (shouldKeepPlayerBarOnError(failedItem != null,
							(failedItem != null) && failedItem.isVideo())) {
						showRetryState();
						showPanel(true);
						break;
					}
				case PlaybackStateCompat.STATE_NONE:
				case PlaybackStateCompat.STATE_STOPPED:
					fireBroadcastEvent(Listener::onPlaybackStopped);
					resetProgressBar();
					showPanel(false);
					break;
				case PlaybackStateCompat.STATE_FAST_FORWARDING:
				case PlaybackStateCompat.STATE_REWINDING:
				case PlaybackStateCompat.STATE_BUFFERING:
					showPanel(true);
					break;
				case PlaybackStateCompat.STATE_CONNECTING:
					showConnectingState();
					showPanel(true);
					break;
				case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
				case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
				case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
					showPanel(true);
					break;
			}

			deliverSnapshot(sessionCallback.getPlaybackSnapshot());
		}

		@Override
		public void onMetadataChanged(@Nullable MediaMetadataCompat metadata) {
			if (!isCurrent()) return;
			PlaybackSnapshot snapshot = sessionCallback.getPlaybackSnapshot();
			deliverSnapshot(snapshot);
			fireBroadcastEvent(l -> l.onPlaybackMetadataChanged(snapshot));
		}

		private void deliverSnapshot(PlaybackSnapshot snapshot) {
			PlaybackSnapshot previous = deliveredSnapshot;
			PlayableItem i = snapshot.getItem();
			// Publish the new snapshot before callbacks query getCurrentItem().
			deliveredSnapshot = snapshot;
			if (!snapshot.hasSameItem(previous)) {
				PlayableItem old = (previous == null) ? null : previous.getItem();
				fireBroadcastEvent(l -> l.onPlayableChanged(old, i));
			}
		}

		void pauseProgressUpdate(boolean pause) {
			pauseProgressUpdate = pause;
		}

		void setProgressTime(int seconds) {
			if (progressTime != null) progressTime.setText(timeToString(seconds));
		}

		private void startProgressUpdate() {
			if ((progressUpdateStamp == null) && ((progressBar != null) || (progressTime != null))) {
				progressUpdateStamp = new Object();
				handler.postDelayed(() -> updateProgress(progressUpdateStamp), 1000);
			}
		}

		private void stopProgressUpdate() {
			progressUpdateStamp = null;
		}

		private void updateProgress(Object stamp) {
			if (!isCurrent() || (progressUpdateStamp != stamp)) return;

			if (!pauseProgressUpdate) {
				MediaEngine eng = sessionCallback.getEngine();
				PlayableItem src = (eng != null) ? eng.getSource() : null;

				if (src != null) {
					eng.getPosition().main().onSuccess(position -> {
						if (!isCurrent() || (progressUpdateStamp != stamp) ||
								(eng != sessionCallback.getEngine())) return;

						int pos = (int) (position / 1000);
						long knownDuration = (duration == null) ? 0L : duration.peek(0L);
						if (knownDuration <= 0L) knownDuration = lastKnownTimelineDuration(src);
						publishTimeline(eng, src, position, knownDuration, true);
						if (progressBar != null) {
							progressBar.setProgress(pos);

							if (updateDuration) {
								eng.getDuration().main().onSuccess(dur -> {
									if (!ownsPlayback(stamp, eng, src)) return;
									if (dur > 0) {
										updateDuration = false;
										int max = (int) (dur / 1000);
										long last = getLib().getLastPlayedPosition(src);
										src.setDuration(dur);
										if (last > 0) eng.setPosition(last);
										progressBar.setMax(max);
										if (progressTotal != null) progressTotal.setText(timeToString(max));
										if (src.isSeekable() && eng.canSeek()) showSeekableTimeline(max, pos);
										publishTimeline(eng, src, position, dur, true);
										fireBroadcastEvent(l -> l.onDurationChanged(src));
									}
								});
							}

							if (src instanceof StreamItem) {
								FutureSupplier<Long> dur = eng.getDuration();

								if (dur != duration) {
									duration = dur;
									dur.onSuccess(d -> {
										if (!ownsPlayback(stamp, eng, src)) return;
										int max = (int) (d / 1000);
										progressBar.setMax(max);
										if (progressTotal != null) progressTotal.setText(timeToString(max));
										if ((max > 0) && src.isSeekable() && eng.canSeek())
											showSeekableTimeline(max, pos);
									});
								}

								src.getMediaDescription().onSuccess(md -> {
									if (!ownsPlayback(stamp, eng, src)) return;
										Bundle b = md.getExtras();
									if (b != null) {
										long start = b.getLong(STREAM_START_TIME, 0);
										long end = b.getLong(STREAM_END_TIME, 0);

										if (start < end) {
											int second = (int) ((System.currentTimeMillis() - start) / 1000);
											progressBar.setSecondaryProgress((second >= progressBar.getProgress()) ?
													second : progressBar.getMax());
											return;
										}
									}
									progressBar.setSecondaryProgress(0);
								});
							}
						}

						setProgressTime(pos);
					});
				}
			}

			handler.postDelayed(() -> updateProgress(stamp), 1000);
		}

		private StringBuilder timeToString(int seconds) {
			timeBuilder.setLength(0);
			TextUtils.timeToString(timeBuilder, seconds);
			return timeBuilder;
		}

		private void playPause(int st) {
			if (!isCurrent()) return;
			PlayableItem i;
			MediaEngine eng = sessionCallback.getEngine();

			if ((eng != null) && ((i = eng.getSource()) != null)) {
				FutureSupplier<Long> getPos = eng.getPosition().main();
				duration = i.isStream() ? eng.getDuration() : i.getDuration();
				duration.main().onCompletion((dur, fail) -> {
					if (!ownsPlayback(eng, i)) return;
					if (fail != null) {
						logProgressFailure(i, fail);
						resetProgressBar();
					} else {
						getPos.onCompletion((pos, f) -> {
							if (!ownsPlayback(eng, i)) return;
							if (f != null) {
								logProgressFailure(i, f);
								resetProgressBar();
							} else if ((sessionCallback.getEngine() == eng) && (i == eng.getSource())) {
								playPause(eng, st, (int) (dur / 1000), (int) (pos / 1000));
							} else {
								resetProgressBar();
							}
						});
					}
				});
			} else {
				resetProgressBar();
			}
		}

		private void logProgressFailure(PlayableItem item, Throwable failure) {
			if (item.isLocationSensitive()) {
				Log.d("Failed to query progress for sensitive item ", item.getId(), " (",
						failure.getClass().getSimpleName(), ')');
			} else {
				Log.d(failure);
			}
		}

		private void playPause(MediaEngine eng, int st, int dur, int pos) {
			if (!isCurrent()) return;
			PlayableItem item = eng.getSource();
			PlaybackTimelinePolicy.Mode timeline = (item == null) ?
					PlaybackTimelinePolicy.Mode.HIDDEN : PlaybackTimelinePolicy.resolve(
					item.isLiveStream(), item.isSeekable(), eng.canSeek(), dur * 1000L);
			boolean canResolveTimeline = (item != null) && item.isSeekable() && eng.canSeek();
			boolean canSeek = timeline == PlaybackTimelinePolicy.Mode.SEEKABLE;
			if (item != null) publishTimeline(eng, item, pos * 1000L,
					dur * 1000L, st == STATE_PLAYING);

			if (canSeek) {
				showSeekableTimeline(dur, pos);
			} else {
				hideTimeline();
				if ((timeline == PlaybackTimelinePolicy.Mode.LIVE) && (liveBadge != null))
					liveBadge.setVisibility(VISIBLE);
			}

			if (st == STATE_PLAYING) {
				updateDuration = (dur <= 0) && canResolveTimeline;
				if (canSeek || canResolveTimeline) startProgressUpdate();
				else stopProgressUpdate();

				if (playPauseButton != null) {
					if (eng.canPause()) {
						playPauseButton.setSelected(false);
						playPauseButton.setActivated(true);
					} else {
						playPauseButton.setSelected(false);
						playPauseButton.setActivated(false);
					}
				}
			} else {
				stopProgressUpdate();

				if (playPauseButton != null) {
					playPauseButton.setSelected(true);
					playPauseButton.setActivated(false);
				}
			}

			showPanel(true);
		}

		private void showPanel(boolean show) {
			if (controlPanel != null) controlPanel.setVisibility(show ? VISIBLE : GONE);
		}

		private void showConnectingState() {
			stopProgressUpdate();
			hideTimeline();
			if (progressTime != null) {
				progressTime.setVisibility(VISIBLE);
				progressTime.setText(R.string.loading);
			}
			if (playPauseButton != null) {
				playPauseButton.setSelected(false);
				playPauseButton.setActivated(false);
			}
		}

		private void showRetryState() {
			stopProgressUpdate();
			hideTimeline();
			if (progressTime != null) {
				progressTime.setVisibility(VISIBLE);
				progressTime.setText(R.string.retry);
			}
			if (playPauseButton != null) {
				playPauseButton.setSelected(true);
				playPauseButton.setActivated(false);
			}
		}

		private boolean ownsPlayback(MediaEngine engine, PlayableItem item) {
			return isCurrent() && (sessionCallback.getEngine() == engine) &&
					(engine.getSource() == item);
		}

		private boolean ownsPlayback(Object stamp, MediaEngine engine, PlayableItem item) {
			return (progressUpdateStamp == stamp) && ownsPlayback(engine, item);
		}

		private void publishTimeline(MediaEngine engine, PlayableItem item,
				long positionMillis, long durationMillis, boolean playing) {
			if (!ownsPlayback(engine, item)) return;
			PlaybackTimelinePolicy.Mode mode = PlaybackTimelinePolicy.resolve(
					item.isLiveStream(), item.isSeekable(), engine.canSeek(), durationMillis);
			PlaybackTimelineSnapshot next = new PlaybackTimelineSnapshot(item,
					ownerToken.generation(), mode, Math.max(0L, positionMillis),
					Math.max(0L, durationMillis), playing);
			playbackTimelineSnapshot = next;
			fireBroadcastEvent(listener -> listener.onPlaybackTimelineChanged(next));
		}

		/** Avoid replacing a resolved seekable timeline with a transient zero-duration read. */
		private long lastKnownTimelineDuration(PlayableItem item) {
			PlaybackTimelineSnapshot timeline = playbackTimelineSnapshot;
			return ((timeline != null) && (timeline.item() == item)) ? timeline.durationMillis() : 0L;
		}

		private void hideTimeline() {
			if (progressBar != null) {
				progressBar.setEnabled(false);
				progressBar.setVisibility(INVISIBLE);
			}
			if (progressTime != null) progressTime.setVisibility(GONE);
			if (progressTotal != null) progressTotal.setVisibility(GONE);
			if (liveBadge != null) liveBadge.setVisibility(GONE);
			if (rwButton != null) rwButton.setVisibility(INVISIBLE);
			if (ffButton != null) ffButton.setVisibility(INVISIBLE);
		}

		private void showSeekableTimeline(int duration, int position) {
			if (liveBadge != null) liveBadge.setVisibility(GONE);
			if (progressBar != null) {
				progressBar.setEnabled(true);
				progressBar.setVisibility(VISIBLE);
				progressBar.setMax(duration);
				progressBar.setProgress(position);
			}
			if (progressTime != null) {
				progressTime.setVisibility(VISIBLE);
				progressTime.setText(timeToString(position));
			}
			if (progressTotal != null) {
				progressTotal.setVisibility(VISIBLE);
				progressTotal.setText(timeToString(duration));
			}
			if (rwButton != null) rwButton.setVisibility(VISIBLE);
			if (ffButton != null) ffButton.setVisibility(VISIBLE);
		}

		private void resetProgressBar() {
			if (!isCurrent()) return;
			if (progressTime != null) progressTime.setVisibility(INVISIBLE);
			if (progressTotal != null) progressTotal.setVisibility(INVISIBLE);
			if (progressBar != null) {
				progressBar.setProgress(0);
				progressBar.setSecondaryProgress(0);
			}
			stopProgressUpdate();
		}

		private boolean isCurrent() {
			return isPresentationBound(this);
		}
	}

	static String normalizePlaybackError(CharSequence error, String fallback) {
		return ((error == null) || (error.length() == 0)) ? fallback : error.toString();
	}

	static boolean shouldKeepPlayerBarOnError(boolean hasItem, boolean video) {
		return hasItem && !video;
	}

	public interface Listener {

		void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem);

		default void onPlaybackStateChanged(PlaybackStateCompat state) {
		}

		default void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
		}

		default void onPlaybackError(String message) {
		}

		default void onPlaybackStopped() {
		}

		default void onDurationChanged(PlayableItem i) {
		}

		default void onPlaybackTimelineChanged(PlaybackTimelineSnapshot snapshot) {
		}
	}
}
