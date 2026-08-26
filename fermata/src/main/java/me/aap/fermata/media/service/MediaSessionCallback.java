package me.aap.fermata.media.service;

import static android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static me.aap.fermata.media.service.PlaybackSnapshot.METADATA_KEY_PREPARATION_STATUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_FAST_FORWARD;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_URI;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_REWIND;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_GROUP;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_FAST_FORWARDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_REWINDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static me.aap.fermata.action.KeyEventHandler.handleKeyEvent;
import static me.aap.fermata.media.engine.MediaEngine.NO_SUBTITLES;
import static me.aap.fermata.media.pref.MediaPrefs.AE_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.BASS_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.BASS_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_BANDS;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_PRESET;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_USER_PRESETS;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_MODE;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.VOL_BOOST_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.VOL_BOOST_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.getUserPresetBands;
import static me.aap.fermata.media.pref.PlaybackControlPrefs.getTimeMillis;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.function.CheckedRunnable.runWithRetry;
import static me.aap.utils.misc.Assert.assertNotNull;
import static me.aap.utils.misc.MiscUtils.ifNotNull;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.EngineSelection;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.media.engine.PlaybackFailureException;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.PersistentMediaItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.net.RemotePlaybackProgress;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackLifecycleItem;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.action.HardwareInputRouter;
import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Async;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Consumer;
import me.aap.utils.holder.Holder;
import me.aap.utils.log.Log;
import me.aap.utils.net.NetServer;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public class MediaSessionCallback extends MediaSessionCompat.Callback
		implements MediaSessionCallbackAssistant, MediaEngine.Listener,
		AudioManager.OnAudioFocusChangeListener, EventBroadcaster<MediaSessionCallback.Listener>,
		BiConsumer<SubGrid.Position, Subtitles.Text>, Closeable, ProgressOwnership {
	public static final String EXTRA_POS = "me.aap.fermata.extra.pos";
	private static final long SUPPORTED_ACTIONS =
			ACTION_PLAY | ACTION_STOP | ACTION_PAUSE | ACTION_PLAY_PAUSE | ACTION_PLAY_FROM_MEDIA_ID |
					ACTION_PLAY_FROM_SEARCH | ACTION_PLAY_FROM_URI | ACTION_SKIP_TO_PREVIOUS |
					ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_QUEUE_ITEM | ACTION_REWIND | ACTION_FAST_FORWARD |
					ACTION_SEEK_TO | ACTION_SET_REPEAT_MODE | ACTION_SET_SHUFFLE_MODE;
	private final Collection<ListenerRef<MediaSessionCallback.Listener>> listeners =
			new LinkedList<>();
	private final MediaLib lib;
	private final FermataMediaService service;
	private final MediaSessionCompat session;
	private final PlaybackControlPrefs playbackControlPrefs;
	private final Handler handler;
	private final HardwareInputRouter hardwareInputRouter;
	private final AudioManager audioManager;
	private final AudioFocusRequestCompat audioFocusReq;
	private final PlaybackCustomActions playbackActions;
	private final BroadcastReceiver onNoisy;
	private MediaEngine engine;
	private ControlOnlyDelegate controlOnlyDelegate;
	private boolean playOnPrepared;
	private boolean playOnAudioFocus;
	private boolean isMuted;
	private boolean tryAnotherEngine;
	private final VideoOutputCoordinator videoOutput = new VideoOutputCoordinator();
	private Queue<Prioritized<MediaSessionCallbackAssistant>> assistants;
	private FutureSupplier<?> playerTask = completedVoid();
	private MediaMetadataCompat metadata;
	private PlaybackSnapshot playbackSnapshot;
	private long playbackSnapshotRevision;
	private long playbackRequestRevision;
	private final PlaybackOwnership playbackOwnership = new PlaybackOwnership();
	private final PlaybackEngineLeaseController playbackEngineLease =
			new PlaybackEngineLeaseController(playbackOwnership, new PlaybackEngineLeaseController.Access() {
				@Override public boolean terminal() { return terminal; }
				@Override public long requestRevision() { return playbackRequestRevision; }
				@Override public void requestRevision(long revision) { playbackRequestRevision = revision; }
				@Override public MediaEngine engineSlot() { return engine; }
				@Override public void engineSlot(MediaEngine value) { engine = value; }
			});
	private final RemotePlaybackLifecycleController playbackLifecycle;
	private final PlaybackTransition playbackTransition = new PlaybackTransition();
	private final PlaybackPreparationStatus preparationStatus = new PlaybackPreparationStatus();
	private final DeferredInitialSeek deferredInitialSeek = new DeferredInitialSeek();
	private final PlaybackProgressPolicy progressPolicy = new PlaybackProgressPolicy();
	private final PlaybackProgressCoordinator progressCoordinator;
	private final PlaybackQueueContext<PlayableItem> playbackQueueContext =
			new PlaybackQueueContext<>(PlayableItemResolver::unwrap);
	private final PlaybackStopTimer playbackStopTimer;
	private final PlaybackAdvanceWatchdog playbackAdvanceWatchdog;
	private volatile boolean terminal;
	private final PermanentFocusLoss permanentFocusLoss;
	private String retryStreamId;
	private int streamRetryAttempt;
	private long streamStartedAt;

	public MediaSessionCallback(FermataMediaService service, MediaSessionCompat session,
															MediaLib lib,
															PlaybackControlPrefs playbackControlPrefs, Handler handler) {
		this.lib = lib;
		this.service = service;
		this.session = session;
		this.playbackControlPrefs = playbackControlPrefs;
		this.handler = handler;
		hardwareInputRouter = new HardwareInputRouter(this);
		permanentFocusLoss = new PermanentFocusLoss(new PermanentFocusLoss.Scheduler() {
			@Override public void postDelayed(Runnable task, long delay) { handler.postDelayed(task, delay); }
			@Override public void removeCallbacks(Runnable task) { handler.removeCallbacks(task); }
		}, () -> terminal, this::onStop);
		playbackStopTimer = new PlaybackStopTimer(
				(task, delay) -> handler.postDelayed(task, delay),
				System::currentTimeMillis, this::onStop);
		playbackAdvanceWatchdog = new PlaybackAdvanceWatchdog(
				(task, delay) -> handler.postDelayed(task, delay), this::onSkipToNext);
		playbackLifecycle = new RemotePlaybackLifecycleController(handler::post);
		progressCoordinator = new PlaybackProgressCoordinator(this, progressPolicy, handler,
				item -> {
					MediaEngine current = getEngine();
					return (current == null) ? null : current.getPosition();
				}, item -> {
					MediaEngine current = getEngine();
					return (current != null) && (current.getSource() == item) &&
							(current.getCurrentSubtitles() != NO_SUBTITLES);
				}, lib);
		Context ctx = lib.getContext();

		playbackActions = new PlaybackCustomActions(ctx);

		PlaybackStateCompat initialState =
				new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS).build();
		setPlaybackState(initialState);
		session.setActive(true);

		audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

		if (audioManager != null) {
			AudioAttributesCompat focusAttrs =
					new AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
							.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build();
			audioFocusReq = new AudioFocusRequestCompat.Builder(
					AudioManagerCompat.AUDIOFOCUS_GAIN).setAudioAttributes(focusAttrs)
					.setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(this).build();
		} else {
			audioFocusReq = null;
		}

			onNoisy = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
					Log.i("Received ACTION_AUDIO_BECOMING_NOISY event");
					onPause();
				}
			}
		};
		ctx.registerReceiver(onNoisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
	}

	public Context getContext() {
		var ctx = getMediaLib().getContext();
		if (BuildConfig.AUTO) {
			var f = ActivityDelegate.getContextToDelegate();
			if (f != null) {
				var d = f.apply(ctx);
				if (d != null) ctx = d.getContext();
			}
		}
		return ctx;
	}

	public MediaLib getMediaLib() {
		return lib;
	}

	public MediaEngineManager getEngineManager() {
		return lib.getMediaEngineManager();
	}

	@Nullable
	public MediaEngine getEngine() {
		return engine;
	}

	/**
	 * A short-lived transport owner for renderers which remain outside Fermata's media-engine
	 * pipeline. It cannot supply a source, decoder, audio focus, position, or stream URL.
	 */
	public interface ControlOnlyDelegate {
		boolean isControlOnlyActive();

		boolean dispatchControlOnlyAction(ControlOnlyAction action);
	}

	public enum ControlOnlyAction { PLAY, PAUSE, NEXT_TRACK }

	public boolean claimControlOnly(@NonNull ControlOnlyDelegate delegate, int state,
			long actions, @Nullable MediaMetadataCompat nextMetadata) {
		if (terminal || !delegate.isControlOnlyActive() || (engine != null)) return false;
		ControlOnlyDelegate current = controlOnlyDelegate;
		if ((current != null) && (current != delegate)) return false;
		controlOnlyDelegate = delegate;
		long supported = ACTION_PLAY | ACTION_PAUSE | ACTION_PLAY_PAUSE;
		if ((actions & ACTION_SKIP_TO_NEXT) != 0) supported |= ACTION_SKIP_TO_NEXT;
		metadata = nextMetadata;
		session.setMetadata(nextMetadata);
		setPlaybackState(new PlaybackStateCompat.Builder().setActions(supported)
				.setState(state, 0L, 1f).build(), null, nextMetadata);
		return true;
	}

	public void releaseControlOnly(@NonNull ControlOnlyDelegate delegate) {
		if (controlOnlyDelegate != delegate) return;
		controlOnlyDelegate = null;
		if (engine != null) return;
		metadata = null;
		session.setMetadata(null);
		setPlaybackState(new PlaybackStateCompat.Builder().setActions(0L)
				.setState(STATE_NONE, 0L, 1f).build(), null, null);
		session.setActive(false);
	}

	private boolean dispatchControlOnlyAction(ControlOnlyAction action) {
		ControlOnlyDelegate delegate = controlOnlyDelegate;
		if ((delegate == null) || !delegate.isControlOnlyActive()) return false;
		try {
			return delegate.dispatchControlOnlyAction(action);
		} catch (RuntimeException error) {
			Log.d(error, "Control-only media action failed");
			return false;
		}
	}

	@Override
	public LastPlayedLease captureLastPlayed(PlayableItem item) {
		return new LastPlayedLease(PlayableItemResolver.unwrap(item), playbackRequestRevision);
	}

	@Override
	public boolean isStillLastPlayedOwner(LastPlayedLease lease) {
		if (lease.playbackRequestRevision() != playbackRequestRevision) return false;
		PlayableItem item = lease.item();
		if (playbackTransition.isPending(item)) return true;
		if (playbackTransition.isPreviousItem(item)) return true;
		return isCurrentEngineSource(item);
	}

	@Override
	public boolean isCurrentEngineSource(PlayableItem item) {
		item = PlayableItemResolver.unwrap(item);
		MediaEngine current = getEngine();
		PlayableItem source = (current == null) ? null : current.getSource();
		return (source != null) && (PlayableItemResolver.unwrap(source) == item);
	}

	public void setEngine(MediaEngine engine) {
		if (terminal) { MediaEngineShutdown.release(engine, audioManager, audioFocusReq); return; }
		switchEngine(engine);
	}

	private void recordPlaybackDiagnostic(String name, DiagnosticScope scope,
			DiagnosticPriority priority, @Nullable MediaEngine mediaEngine,
			@Nullable PlayableItem item, long revision, @Nullable String reason,
			@Nullable String errorClass) {
		try {
			DiagnosticEvent.Builder event = DiagnosticEvent.builder("playback", name)
					.scope(scope).priority(priority)
					.put("revision", revision)
					.put("engine_id", (mediaEngine == null) ? -1 : mediaEngine.getId())
					.put("engine_class", (mediaEngine == null) ? "none" :
							mediaEngine.getClass().getSimpleName())
					.put("engine_fingerprint", (mediaEngine == null) ? 0 :
							System.identityHashCode(mediaEngine))
					.put("item_class", (item == null) ? "none" : item.getClass().getSimpleName())
					.put("item_fingerprint", (item == null) ? 0 : System.identityHashCode(item));
			if (reason != null) event.put("reason", reason);
			if (errorClass != null) event.put("error_class", errorClass);
			FermataApplication.get().getDiagnostics().record(event.build());
		} catch (Throwable ignored) {
			// Diagnostics must never affect playback or media-session callbacks.
		}
	}

	/** Replaces a provisional engine without stopping its pending prepare/play transition. */
	public boolean replaceEngine(@NonNull MediaEngine expected, @NonNull MediaEngine replacement) {
		if (rejectsEngineReplacement(engine, expected, replacement)) {
			recordPlaybackDiagnostic("engine_handoff_rejected", DiagnosticScope.DETAILED,
					DiagnosticPriority.DETAIL, expected, expected.getSource(), playbackRequestRevision,
					"unexpected_current_engine", null);
			return false;
		}
		if (!playbackOwnership.replaceEngine(expected, replacement)) {
			recordPlaybackDiagnostic("engine_handoff_rejected", DiagnosticScope.DETAILED,
					DiagnosticPriority.DETAIL, expected, expected.getSource(), playbackRequestRevision,
					"ownership_mismatch", null);
			return false;
		}
		engine = replacement;
		bindVideoOutput(replacement);
		session.setActive(true);
		recordPlaybackDiagnostic("engine_handoff", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, replacement, replacement.getSource(), playbackRequestRevision,
				"replaced", null);
		return true;
	}

	/** Completes an Activity-backed handoff only while its requested item remains pending. */
	public long capturePendingEngineHandoff(@NonNull MediaEngine expected,
			@NonNull PlayableItem target) {
		target = PlayableItemResolver.unwrap(target);
		return acceptsPendingEngineHandoff(engine, expected,
				playbackTransition.isPending(target)) &&
				playbackOwnership.isRequestCurrent(playbackRequestRevision, target) ?
				playbackRequestRevision : -1L;
	}

	public boolean handoffPendingEngine(@NonNull MediaEngine expected,
				@NonNull MediaEngine replacement, long requestRevision) {
		if (rejectsHandoff(requestRevision, playbackRequestRevision, engine, expected,
				replacement)) {
			recordPlaybackDiagnostic("engine_handoff_rejected", DiagnosticScope.DETAILED,
					DiagnosticPriority.DETAIL, expected, expected.getSource(), requestRevision,
					"stale_request", null);
			return false;
		}
		if (!playbackOwnership.replaceEngine(expected, replacement)) {
			recordPlaybackDiagnostic("engine_handoff_rejected", DiagnosticScope.DETAILED,
					DiagnosticPriority.DETAIL, expected, expected.getSource(), requestRevision,
					"ownership_mismatch", null);
			return false;
		}
		if (engine == expected) {
			engine = replacement;
			bindVideoOutput(replacement);
		}
		session.setActive(true);
		recordPlaybackDiagnostic("engine_handoff", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, replacement, replacement.getSource(), requestRevision,
				"pending_completed", null);
		return true;
	}

	public boolean startExternalPlayback(@NonNull MediaEngine engine) {
		ExternalPlaybackAdmission admission = ExternalPlaybackAdmission.evaluate(playbackOwnership.getPending(),
				playbackTransition, engine.getSource());
		PlayableItem source = admission.source();
		if (!admission.accepted()) {
			recordPlaybackDiagnostic("engine_callback_rejected", DiagnosticScope.ESSENTIAL,
					DiagnosticPriority.WARN, engine, source, playbackRequestRevision, admission.rejectionReason(), null);
			return false;
		}
		boolean sameEngine = engine == getEngine();
		PlaybackOwnership.Token pendingOwner = admission.pendingOwner();
		if ((pendingOwner != null) && sameEngine &&
				(playbackOwnership.bindEngine(pendingOwner, engine) == null)) return false;
		switchEngine(engine);
		boolean alreadyOwns = sameEngine && (pendingOwner == null) && (source != null) &&
				playbackOwnership.owns(engine, PlayableItemResolver.unwrap(source));
		switch (selectExternalPlaybackOwnershipBranch(sameEngine, pendingOwner != null,
				source != null, alreadyOwns)) {
			case PENDING_TARGET_COMPLETION -> {
				if (!playbackOwnership.commit(engine, source)) return false;
				playbackTransition.complete(engine, source);
			}
			case ADOPT_NEW -> adoptPlaybackOwner(PlayableItemResolver.unwrap(source), engine);
			default -> {
			}
		}

		if (!service.requestPlaybackAudioFocus(engine, audioManager, audioFocusReq,
				STATE_PLAYING, source)) {
			Log.i("Audio focus request failed");
			onStop(false);
			return false;
		}

		onEngineStarted(engine);
		return true;
	}

	private void switchEngine(@NonNull MediaEngine engine) {
		if (this.engine == engine) {
			session.setActive(true);
			return;
		}
		playerTask.cancel();
		onStop(false);
		this.engine = engine;
		PlayableItem source = engine.getSource(); bindVideoOutput(engine);
		if (source != null) adoptPlaybackOwner(PlayableItemResolver.unwrap(source), engine);
		session.setActive(true);
		recordPlaybackDiagnostic("engine_selected", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, engine, source, playbackRequestRevision, "active", null);
	}

	private void bindVideoOutput(@NonNull MediaEngine candidate) {
		PlayableItem source = candidate.getSource();
		bindVideoOutput(candidate, (source != null) && source.isVideo());
	}

	private void bindVideoOutput(@NonNull MediaEngine candidate, boolean video) {
		if (video) { VideoView view = getVideoView(); if (view != null) view.beginVideoSource(candidate); }
		videoOutput.bind(candidate, video);
	}

	private long beginPlaybackRequest(@NonNull PlayableItem item,
			@Nullable MediaEngine requestEngine) {
		item = PlayableItemResolver.unwrap(item);
		if (item.isPlaybackTransportCommand()) {
			playbackOwnership.reviseState();
			PlaybackOwnership.Token owner = playbackOwnership.getActive();
			return (owner == null) ? playbackRequestRevision :
					(playbackRequestRevision = owner.generation());
		}
		Object addonIdentity = item.getRoot();
		if (addonIdentity == null) addonIdentity = item.getClass();
		long revision = playbackRequestRevision = playbackOwnership
				.begin(addonIdentity, item, requestEngine).generation();
		recordPlaybackDiagnostic("playback_request_started", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, requestEngine, item, revision,
				"item", null);
		return revision;
	}

	private void adoptPlaybackOwner(@NonNull PlayableItem item, @Nullable MediaEngine ownerEngine) {
		item = PlayableItemResolver.unwrap(item);
		Object addonIdentity = item.getRoot();
		if (addonIdentity == null) addonIdentity = item.getClass();
		playbackRequestRevision = playbackOwnership
				.adopt(addonIdentity, item, ownerEngine).generation();
		recordPlaybackDiagnostic("playback_owner_adopted", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, ownerEngine, item, playbackRequestRevision, "adopted", null);
	}

	private void rollbackPlaybackOwner(@NonNull PlayableItem item, long requestRevision) {
		item = PlayableItemResolver.unwrap(item);
		if (!playbackOwnership.rollback(requestRevision, item)) return;
		PlaybackOwnership.Token owner = playbackOwnership.getActive();
		playbackRequestRevision = (owner == null) ? playbackOwnership.getItemGeneration() :
				owner.generation();
		recordPlaybackDiagnostic("playback_owner_rollback", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, null, item, requestRevision, "rolled_back", null);
	}

	private boolean isPlaybackRequestCurrent(long requestRevision,
			@NonNull PlayableItem item) {
		return !terminal && isPlaybackRequestCurrent(requestRevision, playbackRequestRevision,
				playbackOwnership.getActive(), item);
	}

	static boolean rejectsEngineReplacement(@Nullable MediaEngine currentEngine,
			@NonNull MediaEngine expected, @NonNull MediaEngine replacement) {
		return (expected == replacement) || (currentEngine != expected); }

	static boolean acceptsPendingEngineHandoff(@Nullable MediaEngine currentEngine,
			@NonNull MediaEngine expected, boolean pendingTarget) {
		return (currentEngine == expected) && pendingTarget; }

	static boolean rejectsHandoff(long requestRevision, long liveRevision,
			@Nullable MediaEngine currentEngine, @NonNull MediaEngine expected,
			@NonNull MediaEngine replacement) {
		return (requestRevision < 0L) || (requestRevision != liveRevision) ||
				((currentEngine != expected) && (currentEngine != replacement)); }

	static boolean isPlaybackRequestCurrent(long requestRevision, long liveRevision,
			@Nullable PlaybackOwnership.Token owner, @NonNull PlayableItem item) {
		item = PlayableItemResolver.unwrap(item);
		return (requestRevision == liveRevision) && (owner != null) &&
				(owner.generation() == requestRevision) &&
				(item.isPlaybackTransportCommand() || (owner.itemIdentity() == item)); }

	static ExternalPlaybackOwnershipBranch selectExternalPlaybackOwnershipBranch(
			boolean sameEngine, boolean pendingTarget, boolean sourcePresent,
			boolean alreadyOwns) {
		if (!sameEngine) return ExternalPlaybackOwnershipBranch.SWITCH_ENGINE;
		if (pendingTarget) return ExternalPlaybackOwnershipBranch.PENDING_TARGET_COMPLETION;
		if (!sourcePresent) return ExternalPlaybackOwnershipBranch.NO_SOURCE;
		return alreadyOwns ? ExternalPlaybackOwnershipBranch.ALREADY_OWNED :
				ExternalPlaybackOwnershipBranch.ADOPT_NEW; }

	enum ExternalPlaybackOwnershipBranch { SWITCH_ENGINE, PENDING_TARGET_COMPLETION, ALREADY_OWNED,
		ADOPT_NEW, NO_SOURCE }

	@Nullable
	public PlayableItem getCurrentItem() {
		return playbackTransition.getCurrentItem(getEngine());
	}

	@NonNull
	public PlaybackSnapshot getPlaybackSnapshot() {
		return playbackSnapshot;
	}

	public MediaSessionCompat getSession() {
		return session;
	}

	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() {
		return playbackControlPrefs;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return listeners;
	}

	public void addVideoView(VideoView view, int priority) { videoOutput.add(view, priority); }

	public void removeVideoView(VideoView view) { videoOutput.remove(view); }

	@Nullable
	public VideoView getVideoView() {
		return videoOutput.getSelected();
	}

	public VideoOutputCoordinator getVideoOutputCoordinator() {
		return videoOutput;
	}

	public void addAssistant(MediaSessionCallbackAssistant a, int priority) {
		if (assistants == null) assistants = new PriorityQueue<>(2);
		assistants.add(new Prioritized<>(a, priority));
	}

	public void removeAssistant(MediaSessionCallbackAssistant a) {
		removeFromQueue(assistants, a);
	}

	@NonNull
	public Handler getHandler() {
		return handler;
	}

	@NonNull
	public HardwareInputRouter getHardwareInputRouter() {
		return hardwareInputRouter;
	}

	@NonNull
	public MediaSessionCallbackAssistant getAssistant() {
		if (assistants == null) return this;
		Prioritized<MediaSessionCallbackAssistant> w = assistants.peek();
		return (w == null) ? this : w.obj;
	}

	public boolean hasCustomEngineProvider() {
		return getEngineManager().hasCustomEngineProvider();
	}

	public void setCustomEngineProvider(@NonNull MediaEngineProvider engineProvider) {
		getEngineManager().setCustomEngineProvider(engineProvider);
		if (getEngine() != null) {
			if (isPlaying()) onStop(true).onSuccess(v -> handler.post(this::play));
			else onStop();
		}
	}

	public void removeCustomEngineProvider(MediaEngineProvider engineProvider) {
		if (getEngineManager().removeCustomEngineProvider(engineProvider)) {
			if (isPlaying()) onStop(true).onSuccess(v -> handler.post(this::play));
			else onStop();
		}
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable(Item i) {
		MediaSessionCallbackAssistant a = getAssistant();
		return (a == this) ? MediaSessionCallbackAssistant.super.getPrevPlayable(i) :
				a.getPrevPlayable(i);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable(Item i) {
		MediaSessionCallbackAssistant a = getAssistant();
		return (a == this) ? MediaSessionCallbackAssistant.super.getNextPlayable(i) :
				a.getNextPlayable(i);
	}

	private static <T> boolean removeFromQueue(Queue<Prioritized<T>> q, T t) {
		if (q == null) return false;
		for (Iterator<Prioritized<T>> it = q.iterator(); it.hasNext(); ) {
			if (it.next().obj == t) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
		if (terminal) return true;
		KeyEvent e = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
		if (e == null) return super.onMediaButtonEvent(mediaButtonEvent);
		return handleKeyEvent(this, e, (i, ke) -> super.onMediaButtonEvent(mediaButtonEvent));
	}

	public void close() {
		hardwareInputRouter.close();
		stopImmediately();
		progressCoordinator.cancelCheckpoint();
		progressPolicy.clear();
		session.setActive(false);
		lib.getContext().unregisterReceiver(onNoisy);
		removeBroadcastListeners();
		metadata = null;
	}

	/**
	 * Stops playback synchronously without waiting for a final position query.
	 * This is reserved for hard runtime boundaries such as an Android Auto disconnect, where
	 * releasing the player, network stream and audio focus is more important than an exact final
	 * progress checkpoint.
	 */
	public void stopImmediately() {
		terminal = true;
		permanentFocusLoss.cancel();
		playbackQueueContext.clear();
		playbackOwnership.reviseState();
		playerTask.cancel();
		resetStreamRetry();
		onStop(false);
	}

	@Override
	public void onPrepare() {
		if (terminal) return;
		playerTask.cancel();
		playerTask = prepare();
	}

	public void prepareIfIdle() {
		if (terminal) return;
		if ((getCurrentItem() != null) || !playerTask.isDone()) return;
		onPrepare();
	}

	private FutureSupplier<Void> prepare() {
		if (terminal) return completedVoid();
		int st = getPlaybackState().getState();

		if ((st != PlaybackState.STATE_NONE) && (st != PlaybackState.STATE_ERROR)) {
			return completedVoid();
		}

		return lib.getLastPlayedItem().then(this::prepareItem).then(i -> {
			if (i == null) return completedVoid();
			if (i.isVideo() || !i.isSeekable()) {
				adoptPlaybackOwner(i, null);
				setPlaybackState(createPlayingState(i, STATE_STOPPED, 0, 0, 1f));
				return i.getMediaData().onSuccess(this::setMetadata).cast();
			}

			MediaEngine previous = engine;
			// The legacy factory closes its input; detach native output first.
			if (previous != null) videoOutput.clearIfBound(previous);
			engine = getEngineManager().createEngine(previous, i, this);
			Log.d("MediaEngine ", engine + " created for ", i);
			if (engine == null) return completedVoid();
			adoptPlaybackOwner(i, engine);

			playOnPrepared = false;
			bindVideoOutput(engine);
			tryAnotherEngine = true;
			engine.prepare(i);
			return completedVoid();
		});
	}

	@Override
	public void onPlay() {
		if (dispatchControlOnlyAction(ControlOnlyAction.PLAY)) return;
		if (terminal) return; permanentFocusLoss.cancel();
		playerTask.cancel();
		playerTask = play();
	}

	@SuppressLint("SwitchIntDef")
	public FutureSupplier<Void> play() {
		if (terminal) return completedVoid();
		PlaybackStateCompat state = getPlaybackState();

		switch (state.getState()) {
			case STATE_NONE, STATE_STOPPED, STATE_ERROR -> {
				return lib.getLastPlayedItem().then(this::prepareItem).then(i -> {
					if (i != null) playPreparedItem(i, lib.getLastPlayedPosition(i));
					return completedVoid();
				});
			}
			case STATE_PAUSED -> {
				MediaEngine eng = getEngine();
				assert (eng != null);
				assert (eng.getSource() != null);
				if (!service.requestPlaybackAudioFocus(eng, audioManager, audioFocusReq,
						STATE_PLAYING, eng.getSource())) {
					Log.i("Audio focus request failed");
					return completedVoid();
				}
				long pos = state.getPosition();
				float speed = getSpeed(engine.getSource());
				playbackOwnership.reviseState();
				playOnPrepared = true;
				state =
						new PlaybackStateCompat.Builder(state).setState(PlaybackStateCompat.STATE_PLAYING, pos,
								speed).build();
				setPlaybackState(state);
				eng.setPosition(pos);
				start(eng, speed);
			}
			default -> {
			}
		}

		return completedVoid();
	}

	@Override
	public void onPlayFromMediaId(String mediaId, Bundle extras) {
		if (terminal) return; permanentFocusLoss.cancel();
		playerTask.cancel();
		playerTask = playFromMediaId(mediaId, extras);
	}

	private FutureSupplier<Void> playFromMediaId(String mediaId, Bundle extras) {
		return lib.getItem(mediaId).then(i -> {
			if (i instanceof PlayableItem) {
				return completed(selectPlaybackItem((PlayableItem) i));
			} else if (i instanceof BrowsableItem) {
				return ((BrowsableItem) i).getFirstPlayable().map(pi ->
						(pi == null) ? null : selectPlaybackItem(pi));
			} else {
				return completedNull();
			}
		}).then(this::prepareItem).then(pi -> {
			if (pi != null) {
				long pos = (extras == null) ? 0 : extras.getLong(EXTRA_POS, 0);
				playPreparedItem(pi, pos);
			} else {
				String msg =
						lib.getContext().getResources().getString(R.string.err_failed_to_play, mediaId);
				Log.w(msg);
				PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
						.setState(STATE_ERROR, 0, 1.0f)
						.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
				setPlaybackState(state);
			}

			return completedVoid();
		});
	}

	@Override
	public void onPlayFromSearch(String query, Bundle extras) {
		if (terminal) return; permanentFocusLoss.cancel();
		Log.i("Search query received: " + query);
		MediaSessionCallbackAssistant assistant = getAssistant();
		if ((assistant != this) && assistant.handleVoiceSearch(query)) {
			Log.i("Routing media search to voice command handler: " + query);
			return;
		}
		getMediaLib().getMetadataRetriever().queryId(query).onSuccess(id -> {
			if (id != null) {
				Log.i("Playing media from search: " + id);
				onPlayFromMediaId(id, null);
			} else {
				Log.i("No media found for query: " + query + ". Playing last item");
			}
		});
	}

	@Override
	public void onPause() {
		if (dispatchControlOnlyAction(ControlOnlyAction.PAUSE)) return;
		if (terminal) return;
		PlayableItem i;
		MediaEngine eng = getEngine();
		if ((eng == null) || ((i = eng.getSource()) == null)) return;
		playOnPrepared = false;
		playbackOwnership.reviseState();
		notifyPlaybackLifecycle((l, revision) -> l.onPlaybackAttemptPaused(revision));
		playerTask.cancel();
		PlaybackOwnership.Token pendingOwner = playbackOwnership.getPending();
		PlayableItem pending = ((pendingOwner != null) &&
				(pendingOwner.itemIdentity() instanceof PlayableItem item)) ? item : null;
		PlaybackSnapshot rollback = playbackTransition.cancel();
		if (pending != null) {
			PlayableItem current = PlayableItemResolver.unwrap(i);
			if ((pending == current) && (pendingOwner.engineIdentity() == eng)) {
				playbackOwnership.commit(eng, pending);
			} else {
				rollbackPlaybackOwner(pending, playbackRequestRevision);
			}
		}
		PlaybackOwnership.StateToken stateOwner = playbackOwnership.captureState();

		if (!eng.canPause()) {
			onStop();
			return;
		}

		eng.pause();
		eng.getPosition().and(eng.getSpeed()).main().onSuccess(h -> {
			if ((eng != getEngine()) || !playbackOwnership.owns(stateOwner)) return;
			long qid = getPlaybackState().getActiveQueueItemId();
			setLastPlayed(i, h.value1);
			PlaybackStateCompat state = createPlayingState(i, true, qid, h.value1, h.value2);
			MediaMetadataCompat currentMetadata = (rollback == null) ?
					((playbackSnapshot == null) ? null : playbackSnapshot.getMetadata()) :
					rollback.getMetadata();
			metadata = currentMetadata;
			session.setMetadata(currentMetadata);
			setPlaybackState(state, i, currentMetadata);
		});
	}

	@Override
	public void onStop() {
		playbackQueueContext.clear();
		playbackOwnership.reviseState();
		playerTask.cancel();
		resetStreamRetry();
		onStop(true);
	}

	private FutureSupplier<?> onStop(boolean setPosition) {
		MediaEngine eng = getEngine();
		if (playbackTransition.hasPending()) setPosition = false;

		if (setPosition && (eng != null)) {
			PlayableItem i = eng.getSource();
			if ((i != null) && i.isExternal()) return onStop(eng, -1);
			else return eng.getPosition().main().then(pos -> onStop(eng, pos));
		} else {
			return onStop(eng, -1);
		}
	}

	private FutureSupplier<?> onStop(MediaEngine eng, long pos) {
		boolean current = eng == engine;
		PlayableItem source = MediaEngineShutdown.source(eng);
		if (current) {
			videoOutput.clear();
			engine = null;
		}
		if (current || (eng == null)) {
			MediaEngineShutdown.run("remote_lifecycle", this::cancelPlaybackLifecycle);
			MediaEngineShutdown.run("deferred_seek", deferredInitialSeek::clear);
		}

		if (eng != null) {
			if ((pos != -1) && (source != null)) {
				PlayableItem i = source;
				try {
				if (i != null) {
					i = PlayableItemResolver.unwrap(i);
					PlaybackSnapshot rollback = playbackTransition.cancel();
					publishOutgoingPosition(i, pos, rollback);
					setLastPlayed(i, pos, PlaybackProgressPolicy.isManaged(i));
				}
				} catch (Throwable error) {
					Log.d(error, "Failed to persist final playback position");
				}
			}

			MediaEngineShutdown.release(eng, audioManager, audioFocusReq);
		}

		if (current || (eng == null)) {
			MediaEngineShutdown.run("preparation_status", preparationStatus::clear);
			MediaEngineShutdown.run("preparation_metadata", this::clearPreparationMetadata);
			MediaEngineShutdown.run("playback_transition", playbackTransition::clear);
			boolean hadOwner = playbackOwnership.getActive() != null;
			MediaEngineShutdown.run("playback_owner", playbackOwnership::release);
			playbackRequestRevision = playbackOwnership.getItemGeneration();
			if (hadOwner) recordPlaybackDiagnostic("playback_owner_released",
					DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE, eng,
					source, playbackRequestRevision,
					"released", null);
			MediaEngineShutdown.run("media_session_state", this::stopped);
		}
		return completedVoid();
	}

	private void stopped() {
		if (getPlaybackState().getState() != STATE_STOPPED) {
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(STATE_STOPPED, 0, 1.0f).build();
			setPlaybackState(state);
		}

		session.setQueue(null);
		session.setActive(false);
	}

	@Override
	public void onSeekTo(long position) {
		if (terminal) return;
		MediaEngine eng = getEngine();
		if ((eng == null) || (eng.getSource() == null)) return;

		playbackOwnership.reviseState();
		PlaybackOwnership.StateToken stateOwner = playbackOwnership.captureState();
		eng.getSpeed().onSuccess(speed -> {
			if (!playbackOwnership.owns(stateOwner)) return;
			PlaybackStateCompat state = getPlaybackState();
			eng.setPosition(position);
			PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
			b.setState(state.getState(), position, speed);
			setPlaybackState(b.build());
		});
	}

	@Override
	public void onSkipToPrevious() {
		if (terminal) return; permanentFocusLoss.cancel();
		playerTask.cancel();
		playerTask = skipTo(false, false);
	}

	public void onSkipToPreviousFolder() {
		if (terminal) return; permanentFocusLoss.cancel();
		playerTask.cancel();
		playerTask = skipTo(false, true);
	}

	@Override
	public void onSkipToNext() {
		if (dispatchControlOnlyAction(ControlOnlyAction.NEXT_TRACK)) return;
		if (terminal) return; permanentFocusLoss.cancel();
		playerTask.cancel();
		playerTask = skipTo(true, false);
	}

	public void onSkipToNextFolder() {
		if (terminal) return; permanentFocusLoss.cancel();
		playerTask.cancel();
		playerTask = skipTo(true, true);
	}

	private FutureSupplier<Void> skipTo(boolean next, boolean folder) {
		PlayableItem i;
		MediaEngine eng = getEngine();
		if ((eng == null) || ((i = eng.getSource()) == null)) return completedVoid();
		PlayableItem source = i;

		FutureSupplier<PlayableItem> getItem;
		if (folder) {
			playbackQueueContext.clear();
			var parent = i.getParent();
			getItem = parent.getPlayableChildren(false, true).main().map(children -> {
				if (children == null || children.isEmpty()) return i;
				return next ? children.get(children.size() - 1) : children.get(0);
			});
		} else {
			getItem = completed(playbackQueueContext.navigationItem(i));
		}

		return getItem.then(
				item -> (next ? getNextPlayable(item) : getPrevPlayable(item))
						.then(candidate -> playbackQueueContext.prepareAdvance(
								source, candidate, this::prepareItem))
						.then(pi -> {
							if ((pi != null) && !PlaybackTransportDispatcher.dispatch(pi, eng, getEngine()))
								skipTo(next, pi);
							return completedVoid();
						}));
	}

	private void skipTo(boolean next, PlayableItem i) {
		long pos = i.getPrefs().getPositionPref();
		playPreparedItem(i, pos, next ? STATE_SKIPPING_TO_NEXT : STATE_SKIPPING_TO_PREVIOUS);
	}

	@Override
	public void onRewind() {
		if (terminal) return;
		rewindFastForward(false, 1);
	}

	@Override
	public void onFastForward() {
		if (terminal) return;
		rewindFastForward(true, 1);
	}

	public void rewindFastForward(boolean ff, int multiply) {
		PlaybackControlPrefs pp = getPlaybackControlPrefs();
		rewindFastForward(ff, pp.getRwFfTimePref(), pp.getRwFfTimeUnitPref(), multiply);
	}

	public boolean rewindFastForward(boolean ff, int time, int timeUnit, int multiply) {
		playerTask.cancel();
		PlayableItem i;
		MediaEngine eng = getEngine();
		if ((eng == null) || ((i = eng.getSource()) == null)) return false;
		playbackOwnership.reviseState();
		PlaybackOwnership.StateToken stateOwner = playbackOwnership.captureState();

		playerTask = eng.getDuration().and(eng.getPosition()).main().onSuccess(
				h -> {
					if (playbackOwnership.owns(stateOwner)) {
						rewindFastForward(eng, i, h.value2, h.value1, ff, time, timeUnit, multiply);
					}
				});
		return true;
	}

	private void rewindFastForward(MediaEngine eng, PlayableItem i, long pos, long dur, boolean ff,
																 int time, int timeUnit, int multiply) {
		if (getCurrentItem() != i) return;

		PlaybackStateCompat state = getPlaybackState();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(ff ? STATE_FAST_FORWARDING : STATE_REWINDING, state.getPosition(),
				state.getPlaybackSpeed());
		setPlaybackState(b.build());
		long timeShift = getTimeMillis(dur, time, timeUnit) * Math.max(1, multiply);

		if (ff) {
			dur -= 1000;
			if (dur <= 0) return;
			pos = Math.min(pos + timeShift, dur);
		} else {
			pos -= timeShift;
			if (pos < 0) pos = 0;
		}

		eng.setPosition(pos);
		setPlaybackState(b.setState(state.getState(), pos, state.getPlaybackSpeed()).build());
	}

	@Override
	public void onCustomAction(String action, Bundle extras) {
		if (terminal) return;
		switch (action) {
			case PlaybackCustomActions.REWIND -> onRewind();
			case PlaybackCustomActions.FAST_FORWARD -> onFastForward();
			case PlaybackCustomActions.REPEAT_ENABLE -> repeatEnableDisable(true);
			case PlaybackCustomActions.REPEAT_DISABLE -> repeatEnableDisable(false);
			case PlaybackCustomActions.SHUFFLE_ENABLE -> shuffleEnableDisable(true);
			case PlaybackCustomActions.SHUFFLE_DISABLE -> shuffleEnableDisable(false);
			case PlaybackCustomActions.FAVORITES_ADD -> favoriteAddRemove(true);
			case PlaybackCustomActions.FAVORITES_REMOVE -> favoriteAddRemove(false);
		}
	}

	private void repeatEnableDisable(boolean enable) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		PlaybackStateCompat state = getPlaybackState();
		List<PlaybackStateCompat.CustomAction> actions = state.getCustomActions();
		i.getParent().getPrefs().setRepeatPref(enable);

		playbackActions.setRepeatEnabled(actions, enable);

		setPlaybackState(new PlaybackStateCompat.Builder(state).build());
	}

	private void shuffleEnableDisable(boolean enable) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		PlaybackStateCompat state = getPlaybackState();
		List<PlaybackStateCompat.CustomAction> actions = state.getCustomActions();
		i.getParent().getPrefs().setShufflePref(enable);

		playbackActions.setShuffleEnabled(actions, enable);

		setPlaybackState(new PlaybackStateCompat.Builder(state).build());
	}

	void favoriteAddRemove(boolean add) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		Favorites favorites = lib.getFavorites();
		PlaybackStateCompat state = getPlaybackState();
		List<PlaybackStateCompat.CustomAction> actions = state.getCustomActions();

		playbackActions.setFavorite(actions, add);

		if (add) favorites.addItem(i);
		else favorites.removeItem(i);

		if (i.getParent() == favorites) {
			favorites.getQueue().main().onSuccess(q -> {
				if (i != getCurrentItem()) return;
				session.setQueue(q);
				String id = i.getId();

				for (QueueItem qi : q) {
					if (id.equals(qi.getDescription().getMediaId())) {
						PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
						b.setActiveQueueItemId(qi.getQueueId()).build();
						setPlaybackState(b.build());
					}
				}
			});
		} else {
			setPlaybackState(new PlaybackStateCompat.Builder(state).build());
		}
	}

	@Override
	public void onSkipToQueueItem(long queueId) {
		if (terminal) return;
		permanentFocusLoss.cancel();
		PlayableItem pi = getCurrentItem();
		if (pi == null) return;

		playerTask.cancel();
		playerTask = skipToQueueItem(pi, queueId);
	}

	private FutureSupplier<Void> skipToQueueItem(PlayableItem pi, long queueId) {
		return pi.getParent().getChildren().then(children -> {
			if ((queueId < 0) || (queueId >= children.size())) return completedNull();

			Item i = children.get((int) queueId);
			if (i instanceof PlayableItem) return completed((PlayableItem) i);
			else if (i instanceof BrowsableItem) return ((BrowsableItem) i).getFirstPlayable();
			else return completedNull();
		}).map(i -> (i == null) ? null : selectPlaybackItem(i)).then(this::prepareItem).then(i -> {
			if (i == null) return completedVoid();
			playPreparedItem(i, 0, PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM);
			return completedVoid();
		});
	}

	@Override
	public void onSetShuffleMode(int shuffleMode) {
		if (terminal) return;
		shuffleEnableDisable(shuffleMode != SHUFFLE_MODE_NONE);
	}

	@Override
	public void onSetRepeatMode(int repeatMode) {
		if (terminal) return;
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		BrowsableItemPrefs p = i.getParent().getPrefs();

		switch (repeatMode) {
			case PlaybackStateCompat.REPEAT_MODE_INVALID, REPEAT_MODE_NONE -> {
				p.setRepeatItemPref(null);
				p.setRepeatPref(false);
			}
			case REPEAT_MODE_ONE -> {
				p.setRepeatItemPref(i.getId());
				p.setRepeatPref(false);
			}
			case REPEAT_MODE_ALL, REPEAT_MODE_GROUP -> {
				p.setRepeatItemPref(null);
				p.setRepeatPref(true);
			}
		}
	}

	@Override
	public void onSetPlaybackSpeed(float speed) {
		if (terminal) return;
		MediaEngine eng = getEngine();
		if (eng != null) eng.setSpeed(speed);
	}

	@Override
	public void onEngineBuffering(MediaEngine engine, int percent) {
		if (!acceptsEngineCallback(engine)) return;
		if (isPlaying()) return;
		PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
				.setState(STATE_BUFFERING, 0, 1.0f).build();
		setPlaybackState(state);
	}

	@Override
	public void onEnginePrepared(MediaEngine engine) {
		if (!acceptsEngineCallback(engine)) return;
		notifyPlaybackLifecycle((l, revision) -> l.onPlaybackAttemptPlayerReady(revision));
		playerTask.cancel();
		PlayableItem i = engine.getSource();
		if (i != null) onEnginePrepared(engine, i);
	}

	private void onEnginePrepared(MediaEngine engine, PlayableItem i) {
		if (!acceptsEngineCallback(engine)) return;
		PlayableItem target = PlayableItemResolver.unwrap(i);
		long requestRevision = playbackRequestRevision;
		long pos = playbackTransition.getTargetPosition(target, lib.getLastPlayedPosition(target));
		boolean deferInitialSeek = (target instanceof RemotePlaybackItem remote) &&
				remote.deferInitialSeekUntilFirstFrame();
		long enginePosition = deferredInitialSeek.prepare(engine, target,
				requestRevision, pos, deferInitialSeek);

		if (enginePosition > 0) {
			FutureSupplier<Long> dur = target.getDuration();

			if (dur.isDone()) {
				if (enginePosition <= dur.get(() -> 0L)) engine.setPosition(enginePosition);
			} else {
				dur.main().onSuccess(d -> {
					PlayableItem source = engine.getSource();
					if (!isPlaybackRequestCurrent(requestRevision, target) ||
							(this.engine != engine) || (source == null) ||
							(PlayableItemResolver.unwrap(source) != target)) return;
					engine.setPosition((enginePosition > d) ? 0 : enginePosition);
				});
			}
		}

		float speed = getSpeed(target);
		PlayableItemPrefs prefs = target.getPrefs();
		BrowsableItemPrefs parentPrefs = target.getParent().getPrefs();
		PlaybackControlPrefs playbackPrefs = getPlaybackControlPrefs();
		runWithRetry(() -> setAudiEffects(engine, prefs, parentPrefs, playbackPrefs));

		boolean committed = playbackOwnership.commit(engine, target);
		boolean alreadyCommitted = !committed && playbackOwnership.ownsCommitted(engine, target);
		if (!committed && !alreadyCommitted) return;
		recordPlaybackDiagnostic("playback_owner_commit", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, engine, target, requestRevision,
				alreadyCommitted ? "already_committed" : "committed", null);
		playbackTransition.complete(engine, target);

		if (playOnPrepared) {
			setLastPlayed(target, pos);
			start(engine, speed);
		} else {
			setPlayingState(engine, false, pos, speed);
		}
	}

	@Override
	public void onEngineStarted(MediaEngine engine) {
		if (!acceptsEngineCallback(engine)) return;
		notifyPlaybackLifecycle((l, revision) -> l.onPlaybackAttemptStarted(revision));
		long requestRevision = playbackRequestRevision;
		PlaybackOwnership.StateToken stateOwner = playbackOwnership.captureState();
		PlayableItem source = engine.getSource();
		if (source instanceof StreamItem) streamStartedAt = System.currentTimeMillis();
		else resetStreamRetry();
		engine.getPosition().and(engine.getSpeed()).main()
				.onSuccess(h -> {
					if (ownsEngineState(engine, source, requestRevision, stateOwner))
						setPlayingState(engine, true, h.value1, h.value2, stateOwner);
				});
	}

	@Override
	public void onEnginePreparing(MediaEngine engine, RemotePlaybackProgress progress) {
		PlayableItem item = engine.getSource();
		long requestRevision = playbackRequestRevision;
		handler.post(() -> updatePreparationProgress(engine, item, requestRevision, progress));
	}

	private void updatePreparationProgress(MediaEngine engine, @Nullable PlayableItem item,
			long requestRevision, RemotePlaybackProgress progress) {
		if ((item == null) || !ownsEngineSource(engine, item, requestRevision)) return;
		int playbackState = getPlaybackState().getState();
		if ((playbackState != STATE_CONNECTING) && (playbackState != STATE_BUFFERING) &&
				(playbackState != STATE_PLAYING)) return;
		String detail = switch (progress.phase()) {
			case RESOLVING -> lib.getContext().getString(R.string.p2p_resolving);
			case FINDING_PEERS -> lib.getContext().getString(R.string.p2p_finding_peers);
			case BUFFERING -> lib.getContext().getString(R.string.p2p_buffering_status,
					progress.peers(), Formatter.formatShortFileSize(lib.getContext(),
							progress.downloadRateBytes()), progress.percent());
			case READY -> lib.getContext().getString(R.string.p2p_starting_player_status,
					progress.peers(), Formatter.formatShortFileSize(lib.getContext(),
							progress.downloadRateBytes()));
			case STREAMING -> lib.getContext().getString(R.string.p2p_streaming_status,
					progress.peers(), Formatter.formatShortFileSize(lib.getContext(),
							progress.downloadRateBytes()));
			case REBUFFERING -> lib.getContext().getString(R.string.p2p_rebuffering_status,
					progress.peers(), Formatter.formatShortFileSize(lib.getContext(),
							progress.downloadRateBytes()));
			case FAILED -> {
				PlaybackFailureException.Reason reason = switch (progress.failure()) {
					case METADATA_UNAVAILABLE -> PlaybackFailureException.Reason.P2P_METADATA_UNAVAILABLE;
					case NO_PEERS -> PlaybackFailureException.Reason.P2P_NO_PEERS;
					case DATA_TIMEOUT -> PlaybackFailureException.Reason.P2P_DATA_TIMEOUT;
					case ENGINE_UNAVAILABLE -> PlaybackFailureException.Reason.P2P_ENGINE_UNAVAILABLE;
					case FILE_UNAVAILABLE -> PlaybackFailureException.Reason.P2P_FILE_UNAVAILABLE;
					case LOW_STORAGE -> PlaybackFailureException.Reason.P2P_LOW_STORAGE;
				};
				onEngineError(engine, new PlaybackFailureException(reason));
				yield "";
			}
		};
		if (progress.phase() == RemotePlaybackProgress.Phase.FAILED) return;
		PlayableItem owner = PlayableItemResolver.unwrap(item);
		preparationStatus.update(engine, owner, requestRevision, detail);
		MediaMetadataCompat.Builder builder = (metadata == null) ?
				new MediaMetadataCompat.Builder() : new MediaMetadataCompat.Builder(metadata);
		builder.putString(METADATA_KEY_DISPLAY_TITLE, item.getName());
		builder.putString(METADATA_KEY_PREPARATION_STATUS, detail);
		MediaMetadataCompat progressMetadata = builder.build();
		setMetadata(progressMetadata);
		if ((progress.phase() == RemotePlaybackProgress.Phase.BUFFERING) &&
				(playbackState != STATE_BUFFERING)) {
			PlaybackStateCompat state = new PlaybackStateCompat.Builder(getPlaybackState())
					.setState(STATE_BUFFERING, 0, 1.0f).build();
			setPlaybackState(state);
		}
	}

	private void setPlayingState(MediaEngine engine, boolean playing, long pos, float speed) {
		setPlayingState(engine, playing, pos, speed, playbackOwnership.captureState());
	}

	private void setPlayingState(MediaEngine engine, boolean playing, long pos, float speed,
			PlaybackOwnership.StateToken stateOwner) {
		if (!acceptsEngineCallback(engine)) return;
		long requestRevision = playbackRequestRevision;
		PlayableItem i = engine.getSource();
		if (i == null) return;
		BrowsableItemPrefs prefs = i.getParent().getPrefs();
		int shuffle = prefs.getShufflePref() ? SHUFFLE_MODE_ALL : SHUFFLE_MODE_NONE;
		int repeat;

		if (prefs.getRepeatPref()) {
			repeat = REPEAT_MODE_ALL;
		} else if (i.getId().equals(prefs.getRepeatItemPref())) {
			repeat = REPEAT_MODE_ONE;
		} else {
			repeat = REPEAT_MODE_NONE;
		}

		session.setRepeatMode(repeat);
		session.setShuffleMode(shuffle);

		FutureSupplier<Long> getQid = i.getQueueId();
		Holder<MediaMetadataCompat> mdHolder = new Holder<>();
		Holder<Consumer<MediaMetadataCompat>> update = new Holder<>(mdHolder::set);

		FutureSupplier<Void> load = i.getMediaData().main().then(md1 -> {
			update.get().accept(md1);

			return getQid.then(qid -> i.getMediaDescription().main().then(dsc -> {
				if (!ownsEngineState(engine, i, requestRevision, stateOwner)) return completedVoid();
				MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder(md1);
				FutureSupplier<MediaMetadataCompat> md2 = buildMetadata(b, md1, dsc);

				if (md2.isDone()) {
					update.get().accept(mergePreparationStatus(engine, i, requestRevision,
							md2.get(b::build)));
					return completedVoid();
				} else {
					update.get().accept(mergePreparationStatus(engine, i, requestRevision,
							b.build()));
					return md2.main().then(md3 -> {
						update.get().accept(
								mergePreparationStatus(engine, i, requestRevision, md3));
						return completedVoid();
					});
				}
			}));
		});

		MediaMetadataCompat md;

		if (load.isDone() && !load.isFailed()) {
			md = mdHolder.get();
			assertNotNull(md);
		} else {
			MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
			b.putString(METADATA_KEY_DISPLAY_TITLE, i.getName());
			md = mergePreparationStatus(engine, i, requestRevision, b.build());
			update.set(m -> engine.getPosition().main().onSuccess(position -> {
				if (!ownsEngineState(engine, i, requestRevision, stateOwner)) return;
				PlaybackStateCompat s =
						createPlayingState(i, !isPlaying(), getQid.peek(0L), position, speed);
				setSessionMetadata(m, false);
				setPlaybackState(s);
			}));
		}

		if (!ownsEngineState(engine, i, requestRevision, stateOwner)) return;
		PlaybackStateCompat s = createPlayingState(i, !playing, getQid.peek(0L), pos, speed);
		setSessionMetadata(md, false);
		setPlaybackState(s);
	}

	private MediaMetadataCompat mergePreparationStatus(MediaEngine engine, PlayableItem item,
			long requestRevision, MediaMetadataCompat metadata) {
		PlayableItem owner = PlayableItemResolver.unwrap(item);
		return preparationStatus.merge(engine, owner, requestRevision, metadata);
	}

	private FutureSupplier<MediaMetadataCompat> buildMetadata(MediaMetadataCompat.Builder b,
																														MediaMetadataCompat meta,
																														MediaDescriptionCompat dsc) {
		ifNotNull(dsc.getTitle(), t -> b.putString(METADATA_KEY_DISPLAY_TITLE, t.toString()));
		ifNotNull(dsc.getSubtitle(), t -> b.putString(METADATA_KEY_DISPLAY_SUBTITLE, t.toString()));
		if (meta.getBitmap(METADATA_KEY_ALBUM_ART) != null) return completed(b.build());

		String art = meta.getString(METADATA_KEY_ALBUM_ART_URI);

		if (art != null) {
			b.putString(METADATA_KEY_ALBUM_ART_URI, null);
			return lib.getBitmap(art).then(bm -> {
				b.putBitmap(METADATA_KEY_ALBUM_ART, (bm != null) ? bm : getDefaultImage());
				return completed(b.build());
			});
		}

		Uri uri = dsc.getIconUri();

		if (uri != null) {
			return lib.getBitmap(uri.toString()).then(bm -> {
				b.putBitmap(METADATA_KEY_ALBUM_ART, (bm != null) ? bm : getDefaultImage());
				return completed(b.build());
			});
		}

		b.putBitmap(METADATA_KEY_ALBUM_ART, getDefaultImage());
		return completed(b.build());
	}

	@Override
	public void onEngineEnded(MediaEngine engine) {
		if (!acceptsEngineCallback(engine)) return;
		notifyPlaybackLifecycle((l, revision) -> l.onPlaybackAttemptEnded(revision));
		playerTask.cancel();
		playerTask = engineEnded(engine);
	}

	private FutureSupplier<?> engineEnded(MediaEngine engine) {
		PlayableItem i = engine.getSource();

		if (i != null) {
			if (i instanceof StreamItem) {
				long now = System.currentTimeMillis();
				long played = (streamStartedAt == 0L) ? 0L : Math.max(0L, now - streamStartedAt);
				boolean sameStream = i.getId().equals(retryStreamId);
				int attempt = StreamRetryPolicy.nextAttempt(streamRetryAttempt, sameStream, played);

				if (!StreamRetryPolicy.canRetry(attempt)) {
					Log.w("Stream retry limit reached: ", i);
					resetStreamRetry();
					onStop(false);
					return completedVoid();
				}

				retryStreamId = i.getId();
				streamRetryAttempt = attempt;
				long delay = StreamRetryPolicy.delay(attempt);
				Log.w("Stream ended. Retrying in ", delay, " ms (", attempt, "/",
						StreamRetryPolicy.MAX_ATTEMPTS, "): ", i);
				return Async.<Object>schedule(() -> {
					if ((engine != getEngine()) || (engine.getSource() != i))
						return completedVoid().cast();
					return createPlayItemTask(i, 0).cast();
				}, delay);
			}

			if (i.isVideo()) i.getPrefs().setWatchedPref(true);

			if (!i.getParent().getPrefs().getPlayNextPref()) {
				onStop(true);
				return completedVoid();
			}

			PlayableItem source = i;
			PlayableItem navigationItem = playbackQueueContext.navigationItem(source);
			return getNextPlayable(navigationItem)
					.then(candidate -> playbackQueueContext.prepareAdvance(
							source, candidate, this::prepareItem)).then(next -> {
				if (next != null) {
					skipTo(true, next);
				} else {
					setLastPlayed(i, 0);
					onStop(false);
				}

				return completedVoid();
			});
		} else {
			onStop(false);
			return completedVoid();
		}
	}

	@Override
	public void onVideoSizeChanged(MediaEngine engine, int width, int height) {
		if (!acceptsEngineCallback(engine)) return;
		VideoView v = getVideoView();
		if (v != null) v.setSurfaceSize(engine, width, height);
	}

	@Override
	public void onVideoFirstFrame(MediaEngine engine) {
		if (!acceptsEngineCallback(engine)) return;
		VideoView video = getVideoView();
		if (video != null) {
			video.setSurfaceSize(engine);
			video.revealVideoSurface(engine);
		}
		PlayableItem item = engine.getSource();
		recordPlaybackDiagnostic("engine_first_frame", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.STATE, engine, item, playbackRequestRevision, "rendered", null);
		if (item != null) {
			PlayableItem owner = PlayableItemResolver.unwrap(item);
			if (preparationStatus.complete(engine, owner, playbackRequestRevision) &&
					(metadata != null)) {
				clearPreparationMetadata();
			}
		}
		notifyPlaybackLifecycle((l, revision) -> l.onPlaybackAttemptFirstFrame(revision));
		long deferredPosition = deferredInitialSeek.consume(engine, item,
				playbackRequestRevision);
		if (deferredPosition > 0L) engine.setPosition(deferredPosition);
	}

	private void clearPreparationMetadata() {
		if ((metadata == null) ||
				TextUtils.isEmpty(metadata.getString(METADATA_KEY_PREPARATION_STATUS))) return;
		setMetadata(PlaybackPreparationStatus.clearMetadata(metadata));
	}

	@Override
	public void onSubtitleStreamChanged(MediaEngine engine, @Nullable SubtitleStreamInfo info) {
		if (!acceptsEngineCallback(engine)) return;
		fireBroadcastEvent(l -> l.onSubtitleStreamChanged(this, info));
	}

	@Override
	public void onSubtitleLoadFailed(MediaEngine engine, Throwable error) {
		handler.post(() -> {
			if (!acceptsEngineCallback(engine)) return;
			String name = lib.getContext().getString(R.string.subtitles);
			String message = lib.getContext().getString(R.string.err_failed_to_download, name);
			fireBroadcastEvent(l -> l.onSubtitleLoadFailed(this, message));
		});
	}

	@Override
	public void onEngineError(MediaEngine engine, Throwable ex) {
		if (!acceptsEngineCallback(engine)) return;
		String msg;
		PlayableItem i = engine.getSource();
		recordPlaybackDiagnostic("engine_error", DiagnosticScope.ESSENTIAL,
				DiagnosticPriority.ERROR, engine, i, playbackRequestRevision, null,
				(ex == null) ? "unknown" : ex.getClass().getSimpleName());
		boolean sensitive = (i != null) && i.isLocationSensitive();
		PlaybackFailureException safeFailure = PlaybackFailureException.find(ex);
		String safeCause = (safeFailure == null) ? null : switch (safeFailure.getReason()) {
			case P2P_METADATA_UNAVAILABLE ->
					lib.getContext().getString(R.string.err_p2p_metadata_unavailable);
			case P2P_NO_PEERS -> lib.getContext().getString(R.string.err_p2p_no_peers);
			case P2P_DATA_TIMEOUT -> lib.getContext().getString(R.string.err_p2p_data_timeout);
			case P2P_ENGINE_UNAVAILABLE ->
					lib.getContext().getString(R.string.err_p2p_engine_unavailable);
			case P2P_FILE_UNAVAILABLE ->
					lib.getContext().getString(R.string.err_p2p_file_unavailable);
			case P2P_LOW_STORAGE ->
					lib.getContext().getString(R.string.err_p2p_low_storage);
		};

		if (safeCause != null) {
			msg = lib.getContext().getResources()
					.getString(R.string.err_failed_to_play_cause, i, safeCause);
		} else if (sensitive || TextUtils.isEmpty(ex.getLocalizedMessage())) {
			msg = lib.getContext().getResources().getString(R.string.err_failed_to_play, i);
		} else {
			msg = lib.getContext().getResources()
					.getString(R.string.err_failed_to_play_cause, i, ex.getLocalizedMessage());
		}

		if (sensitive && (safeFailure != null)) {
			Log.w("Failed to play sensitive item ", i.getId(), " (",
					safeFailure.getReason(), ')');
		} else if (sensitive) {
			Log.w("Failed to play sensitive item ", i.getId(), " (",
					ex.getClass().getSimpleName(), ')');
		} else {
			Log.w(ex, msg);
		}

		boolean transportFailure = (safeFailure != null) && safeFailure.preventsEngineFallback();
		MediaEngine failedEngine = engine;
		boolean fallbackCandidate = tryAnotherEngine && !transportFailure &&
				(failedEngine.getSource() != null);
		boolean lifecycleAllowsFallback = !fallbackCandidate || playbackLifecycle.allowsFallback();
		if (fallbackCandidate && lifecycleAllowsFallback) {
			// createAnotherEngine() closes its input; detach native output first.
			videoOutput.clearIfBound(failedEngine);
			MediaEngine replacement = getEngineManager().createAnotherEngine(failedEngine, this);

			if (replacement != null) {
				if ((engine != failedEngine) ||
						!playbackOwnership.replaceEngine(failedEngine, replacement)) {
					replacement.close();
					if (engine != failedEngine) return;
				} else {
					this.engine = replacement;
					Log.i("Trying another engine: ", this.engine);
					tryAnotherEngine = false;
					bindVideoOutput(replacement, i.isVideo());
					this.engine.prepare(i);
					return;
				}
			}
		}
		notifyPlaybackLifecycle((l, revision) -> l.onPlaybackAttemptFailed(revision, ex));

		PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
				.setState(STATE_ERROR, 0, 1.0f)
				.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
		setPlaybackState(state);
		onStop();
	}

	@Override
	public void accept(SubGrid.Position position, Subtitles.Text text) {
		if (metadata == null ||
				(position != SubGrid.Position.BOTTOM_CENTER && position != SubGrid.Position.BOTTOM_LEFT))
			return;

		if (text == null) {
			setSessionMetadata(metadata, true);
			return;
		}

		String t1;
		String t2;
		if (text.getTranslation() != null) {
			t1 = text.getText();
			t2 = text.getTranslation();
		} else {
			var txt = text.getText().trim();
			int idx = txt.lastIndexOf(' ', txt.length() / 2);
			if (idx == -1) {
				t1 = txt;
				t2 = "";
			} else {
				t1 = txt.substring(0, idx);
				t2 = txt.substring(idx + 1);
			}
		}

		var t = metadata.getText(METADATA_KEY_DISPLAY_TITLE);
		var s = metadata.getText(METADATA_KEY_DISPLAY_SUBTITLE);
		if (t1.equals(t == null ? "" : t.toString()) && t2.equals(s == null ? "" : s.toString()))
			return;
		var b = new MediaMetadataCompat.Builder(metadata);
		b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, t1);
		b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, t2);
		setMetadata(b.build());
	}

	private void setMetadata(MediaMetadataCompat metadata) {
		this.metadata = metadata;
		setSessionMetadata(metadata, true);
	}

	private void setSessionMetadata(MediaMetadataCompat metadata, boolean notify) {
		PlaybackSnapshot previous = playbackSnapshot;
		PlaybackSnapshot current = updatePlaybackSnapshot(getPlaybackState(), metadata, getCurrentItem());
		session.setMetadata(metadata);
		if (notify) fireBroadcastEvent(l -> l.onPlaybackSnapshotChanged(this, previous, current));
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		Log.i("Audio focus event received: ", focusChange);

		switch (focusChange) {
			case AUDIOFOCUS_GAIN:
				if (playOnAudioFocus) {
					playOnAudioFocus = false;
					onPlay();
				} else if (isMuted) {
					isMuted = false;
					var eng = getEngine();
					if (eng != null) eng.unmute(getContext());
				}
				break;
			case AUDIOFOCUS_LOSS:
				playOnAudioFocus = false;
				if (isPlaying()) onPause();
				permanentFocusLoss.schedule();
				break;
			case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				break;
			case AUDIOFOCUS_LOSS_TRANSIENT:
				if (!isPlaying()) return;
				var eng = getEngine();
				if ((eng != null) && eng.muteOnTransientFocusLoss()) {
					isMuted = true;
					eng.mute(getContext());
					return;
				}
			default:
				if (isPlaying()) {
					playOnAudioFocus = true;
					onPause();
				}

				break;
		}
	}

	public void playItem(PlayableItem i, long pos) {
		if (terminal) return;
		permanentFocusLoss.cancel();
		i = selectPlaybackItem(i);
		playerTask.cancel();
		resetStreamRetry();
		playerTask = createPlayItemTask(i, pos);
	}

	private PlayableItem selectPlaybackItem(PlayableItem presented) {
		return playbackQueueContext.selectAndCanonicalize(presented);
	}

	/** Persists the current managed item without changing playback or media-session state. */
	public FutureSupplier<Void> saveCurrentPlaybackProgress() {
		MediaEngine current = getEngine();
		if (current == null) return completedVoid();
		PlayableItem source = current.getSource();
		if (source == null) return completedVoid();
		source = PlayableItemResolver.unwrap(source);
		if (!PlaybackProgressPolicy.isManaged(source)) return completedVoid();

		PlayableItem item = source;
		long generation = playbackRequestRevision;
		return current.getPosition().and(item.getDuration()).main().then(values -> {
			if ((generation != playbackRequestRevision) || (current != getEngine())) {
				return completedVoid();
			}
			PlayableItem active = current.getSource();
			if ((active == null) || (PlayableItemResolver.unwrap(active) != item)) {
				return completedVoid();
			}
			return progressPolicy.lifecycle(item, generation, values.value1, values.value2,
					true, false);
		}).ifFail(error -> {
			Log.w(error, "Failed to save current playback progress");
			return null;
		});
	}

	private FutureSupplier<?> createPlayItemTask(PlayableItem i, long pos) {
		i = PlayableItemResolver.unwrap(i);
		PlayableItem target = i;
		MediaEngine eng = getEngine();
		long requestRevision = beginPlaybackRequest(target, eng);
		FutureSupplier<PlayableItem> prepare = captureOutgoingPosition(eng, requestRevision).then(ignored -> {
			if (!isPlaybackRequestCurrent(requestRevision, target)) return completedNull();
			PlayableItem current = (eng == null) ? null : eng.getSource();
			if (current != null) current = PlayableItemResolver.unwrap(current);
			publishPlaybackTransition(target, current, STATE_CONNECTING, pos, requestRevision);
			return prepareItem(target);
		});
		prepare.onSuccess(pi -> {
			if ((pi != null) && isPlaybackRequestCurrent(requestRevision, target))
				playPreparedItem(pi, pos, STATE_CONNECTING, requestRevision);
		}).onFailure(error -> {
			if (!isPlaybackRequestCurrent(requestRevision, target)) return;
			PlaybackSnapshot previous = playbackTransition.getPreviousSnapshot(target);
			if (!playbackTransition.cancelIfPending(target)) {
				rollbackPlaybackOwner(target, requestRevision);
				return;
			}
			rollbackPlaybackOwner(target, requestRevision);
			if (previous != null) {
				metadata = previous.getMetadata();
				session.setMetadata(metadata);
				setPlaybackState(previous.getState(), previous.getItem(), metadata);
				return;
			}
			String msg = (error.getLocalizedMessage() == null) ? error.toString() :
					error.getLocalizedMessage();
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(STATE_ERROR, 0, 1.0f)
					.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
			setPlaybackState(state, target, null);
		});
		return prepare;
	}

	public void onEngineMetadataChanged(MediaEngine engine) {
		if (!acceptsEngineCallback(engine) || engine.getSource() == null) return;
		long requestRevision = playbackRequestRevision;
		PlaybackOwnership.StateToken stateOwner = playbackOwnership.captureState();
		PlayableItem expectedSource = PlayableItemResolver.unwrap(engine.getSource());
		engine.getPosition().and(engine.getSpeed()).main().onSuccess(values -> {
			if (ownsEngineState(engine, expectedSource, requestRevision, stateOwner))
				setPlayingState(engine, isPlaying(), values.value1, values.value2, stateOwner);
		});
	}

	private boolean acceptsEngineCallback(@NonNull MediaEngine callbackEngine) {
		if (terminal) return false;
		PlayableItem source = callbackEngine.getSource();
		boolean sourcePresent = source != null;
		if (usesTokenBackedAuthority(sourcePresent,
				sourcePresent && (playbackOwnership.getActive() != null))) {
			boolean accepted = playbackOwnership.owns(callbackEngine,
					PlayableItemResolver.unwrap(source));
			if (!accepted) recordPlaybackDiagnostic("engine_callback_rejected",
					DiagnosticScope.DETAILED, DiagnosticPriority.DETAIL, callbackEngine, source,
					playbackRequestRevision, "ownership_mismatch", null);
			return accepted;
		}
		boolean ownsPending = (source != null) &&
				playbackTransition.isPending(PlayableItemResolver.unwrap(source));
		boolean accepted = acceptsCallbackOwnership(callbackEngine == getEngine(),
				playbackTransition.hasPending(), ownsPending);
		if (!accepted) recordPlaybackDiagnostic("engine_callback_rejected", DiagnosticScope.DETAILED,
				DiagnosticPriority.DETAIL, callbackEngine, source, playbackRequestRevision,
				"not_current_or_pending", null);
		return accepted;
	}

	private boolean ownsEngineSource(@NonNull MediaEngine callbackEngine,
			@Nullable PlayableItem expectedSource, long requestRevision) {
		if ((expectedSource == null) || !isPlaybackRequestCurrent(requestRevision, expectedSource) ||
				!acceptsEngineCallback(callbackEngine))
			return false;
		PlayableItem current = callbackEngine.getSource();
		return matchesExpectedSource(current, expectedSource);
	}

	private boolean ownsEngineState(@NonNull MediaEngine callbackEngine,
			@Nullable PlayableItem expectedSource, long requestRevision,
			PlaybackOwnership.StateToken stateOwner) {
		boolean stateTokenValid = playbackOwnership.owns(stateOwner);
		return acceptsEngineState(stateTokenValid, stateTokenValid &&
				ownsEngineSource(callbackEngine, expectedSource, requestRevision));
	}

	static boolean usesTokenBackedAuthority(boolean sourcePresent, boolean activeTokenPresent) {
		return sourcePresent && activeTokenPresent;
	}

	/** Compares canonical identities so callers may pass wrapped or already-unwrapped items. */
	static boolean matchesExpectedSource(@Nullable PlayableItem current, @Nullable PlayableItem expected) {
		return (current != null) && (expected != null) && (PlayableItemResolver.unwrap(current) == PlayableItemResolver.unwrap(expected));
	}

	static boolean acceptsEngineState(boolean stateTokenValid, boolean sourceOwned) {
		return stateTokenValid && sourceOwned;
	}

	static boolean acceptsCallbackOwnership(boolean currentEngine, boolean pendingTransition,
			boolean callbackOwnsPending) {
		return currentEngine && (!pendingTransition || callbackOwnsPending);
	}

	static boolean shouldClearPlaybackSurfaces(boolean targetIsVideo, @Nullable Object current,
			@NonNull Object target) {
		return targetIsVideo && ((current == null) || !current.equals(target));
	}

	private FutureSupplier<Void> captureOutgoingPosition(@Nullable MediaEngine eng,
			long requestRevision) {
		if ((eng == null) || playbackTransition.hasPending()) return completedVoid();
		PlayableItem source = eng.getSource();
		if (source == null) return completedVoid();
		source = PlayableItemResolver.unwrap(source);
		PlayableItem outgoing = source;

		return eng.getPosition().main().map(position -> {
			if (requestRevision != playbackRequestRevision) return (Void) null;
			MediaEngine current = getEngine();
			PlayableItem currentSource = (current == null) ? null : current.getSource();
			if ((current == eng) && (currentSource != null) &&
					(PlayableItemResolver.unwrap(currentSource) == outgoing)) {
				publishOutgoingPosition(outgoing, position, null);
			}
			return (Void) null;
		}).ifFail(error -> {
			Log.w(error, "Failed to capture outgoing playback position");
			return (Void) null;
		});
	}

	private void resetStreamRetry() {
		retryStreamId = null;
		streamRetryAttempt = 0;
		streamStartedAt = 0L;
	}

	private void playPreparedItem(PlayableItem i, long pos) {
		i = PlayableItemResolver.unwrap(i);
		playPreparedItem(i, pos, STATE_CONNECTING, beginPlaybackRequest(i, getEngine()));
	}

	private void playPreparedItem(PlayableItem i, long pos, int transitionState) {
		i = PlayableItemResolver.unwrap(i);
		playPreparedItem(i, pos, transitionState, beginPlaybackRequest(i, getEngine()));
	}

	private void playPreparedItem(PlayableItem i, long pos, int transitionState,
			long requestRevision) {
		PlayableItem target = PlayableItemResolver.unwrap(i);
		if (!isPlaybackRequestCurrent(requestRevision, target)) return;
		activatePlaybackLifecycle(target, requestRevision);
		MediaEngine eng = getEngine();

		if (eng != null) {
			PlayableItem current = eng.getSource();

			if ((current != null) && !current.isExternal()) {
				if (current instanceof StreamItem) {
					playPreparedItem(eng, target, pos, current, 0, transitionState,
							requestRevision);
				} else {
					eng.getPosition().main()
							.onSuccess(currentPos -> {
								if (isPlaybackRequestCurrent(requestRevision, target) &&
										(eng == getEngine())) {
									playPreparedItem(eng, target, pos, current, currentPos,
											transitionState, requestRevision);
								}
							});
				}
				return;
			}
		}

		playPreparedItem(eng, target, pos, null, -1, transitionState, requestRevision);
	}

	private void activatePlaybackLifecycle(@NonNull PlayableItem item, long requestRevision) {
		RemotePlaybackLifecycleItem lifecycle =
				(item instanceof RemotePlaybackLifecycleItem value) ? value : null;
		playbackLifecycle.activate(item, lifecycle, requestRevision, error -> {
			MediaEngine current = getEngine();
			if ((current == null) || !acceptsEngineCallback(current)) return;
			onEngineError(current, error);
		});
	}

	private void cancelPlaybackLifecycle() {
		playbackLifecycle.cancel();
	}

	private void notifyPlaybackLifecycle(
			java.util.function.BiConsumer<RemotePlaybackLifecycleItem, Long> notification) {
		playbackLifecycle.notifyActive(notification);
	}

	private boolean publishPlaybackFailure(@NonNull PlaybackEngineLease.FailureClaim claim, @NonNull PlaybackStateCompat errorState) {
		if (playbackEngineLease.rollbackFailure(claim) == null) return false;
		playbackTransition.cancelIfPending(claim.target()); setPlaybackState(errorState, claim.target(), null);
		return claim.consume(); }

	private void playPreparedItem(MediaEngine eng, PlayableItem i, long pos, PlayableItem current, long currentPos, int transitionState, long requestRevision) {
		i = PlayableItemResolver.unwrap(i); if (!isPlaybackRequestCurrent(requestRevision, i)) return;
		if (current != null) current = PlayableItemResolver.unwrap(current); boolean clearPlaybackSurfaces = shouldClearPlaybackSurfaces(i.isVideo(), current, i);
		if ((current != null) && !playbackTransition.isPending(i)) publishOutgoingPosition(current, currentPos, null);
		persistCommittedOutgoing(eng, i, current, currentPos);
		PlaybackEngineLease.Captured captured = playbackEngineLease.capture(requestRevision, i, eng); if (captured == null) return;
		EngineSelection engineSelection = getEngineManager().createEngineSelection(eng, i, this);
		PlaybackEngineLease.Selected selected = playbackEngineLease.select(captured, engineSelection);

		if (selected.candidate() == null) {
			PlaybackEngineLease.FailureClaim claim = playbackEngineLease.tryClaimUnsupported(selected); if (claim == null) return;
			videoOutput.clearIfBound(eng); retireResolvedEngine(eng, engineSelection);
			String msg = lib.getContext().getResources().getString(R.string.err_unsupported_source_type, i);
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(STATE_ERROR, 0, 1.0f)
					.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
			publishPlaybackFailure(claim, state); return;
		}

		PlaybackEngineLease.Accepted accepted = playbackEngineLease.tryAccept(selected); if (accepted == null) return;
		MediaEngine candidate = accepted.candidate();
		if ((candidate != eng) && retireResolvedEngine(eng, engineSelection) &&
				!playbackEngineLease.isCurrent(accepted)) return;

		BrowsableItem p = i.getParent(); boolean updateQueue = false;

		boolean sameItem = (current != null) && current.equals(i);
		boolean sameParent = (current != null) && !sameItem && p.equals(current.getParent());
		switch (PlaybackPreparedItemDecisions.queueAction(current != null, sameItem, sameParent)) {
			case SEEK_SAME_ITEM -> {
				if (pos != -1) {
					if (!playbackEngineLease.isCurrent(accepted)) return; candidate.setPosition(pos);
					if (!playbackEngineLease.isCurrent(accepted)) return;
				}
			}
			case KEEP_QUEUE -> { }
			case REFRESH_QUEUE -> updateQueue = true;
		}
		if (i.isVideo() && playbackEngineLease.isCurrent(accepted)) {
			VideoView view = getVideoView(); if (view != null) {
				if (clearPlaybackSurfaces) view.clearPlaybackSurfaces(candidate); else view.beginVideoSource(candidate); }
		}
		if (!playbackEngineLease.isCurrent(accepted)) return;
		videoOutput.bind(candidate, i.isVideo());
		if (!playbackEngineLease.isCurrent(accepted)) return;

		playOnPrepared = true;
		tryAnotherEngine = true;

		if (!playbackEngineLease.isCurrent(accepted)) return;
		if (!service.requestPlaybackAudioFocus(candidate, audioManager, audioFocusReq,
				transitionState, accepted.target())) {
			Log.i("Audio focus request failed");
			PlaybackEngineLease.FailureClaim claim = playbackEngineLease.tryClaimFailure(accepted); if (claim == null) return;
			videoOutput.clearIfBound(candidate);
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(STATE_ERROR, 0, 1.0f)
					.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
							"Audio focus request failed").build();
			if (publishPlaybackFailure(claim, state)) playbackEngineLease.disposeFailed(accepted);
			return;
		}
		if (!playbackEngineLease.isCurrent(accepted)) return;

		if (!publishPlaybackTransition(i, current, transitionState, pos, accepted.requestRevision(), accepted)) return;
		if (!playbackEngineLease.isCurrent(accepted)) return; candidate.prepare(accepted.target());

		if (updateQueue) {
			PlayableItem queueItem = accepted.target();
			p.getQueue().main().onSuccess(q -> {
				MediaEngine currentEngine = engine; PlayableItem source = (currentEngine == null) ? null : currentEngine.getSource();
				if (PlaybackPreparedItemDecisions.shouldPublishQueue(
						isPlaybackRequestCurrent(requestRevision, queueItem), source, queueItem)) session.setQueue(q);
			});
		}
	}

	private boolean retireResolvedEngine(@Nullable MediaEngine previous, EngineSelection selection) {
		if ((previous == null) || (selection.retirement() != EngineSelection.Retirement.RETIRE_AFTER_RESOLUTION)) return false;
		videoOutput.clearIfBound(previous); MediaEngineShutdown.run("engine_close", previous::close);
		return true;
	}

	private void persistCommittedOutgoing(@Nullable MediaEngine outgoingEngine,
			@NonNull PlayableItem target,
			@Nullable PlayableItem current, long currentPosition) {
		boolean directCurrent = (current != null) && (!playbackTransition.hasPending() ||
			current.equals(target) || playbackTransition.isPreviousItem(current));
		if (PlaybackPreparedItemDecisions.outgoingSource(current != null, directCurrent) ==
				PlaybackPreparedItemDecisions.OutgoingSource.DIRECT_CURRENT) {
			long generation = playbackOwnership.committedGeneration(outgoingEngine, current);
			if (generation >= 0L) {
				setLastPlayed(current, currentPosition, !current.equals(target), generation);
			}
			return;
		}

		PlaybackSnapshot previous = playbackTransition.getPreviousSnapshot(target);
		if ((previous == null) || !previous.canPersistProgress()) return;
		PlayableItem committed = previous.getItem();
		if (committed != null) {
			committed = PlayableItemResolver.unwrap(committed);
			long generation = playbackOwnership.committedGeneration(outgoingEngine, committed);
			if (generation >= 0L) {
				setLastPlayed(committed, previous.getState().getPosition(), true, generation);
			}
		}
	}

	private void publishPlaybackTransition(PlayableItem target, @Nullable PlayableItem current, int transitionState, long targetPosition, long requestRevision) {
		publishPlaybackTransition(target, current, transitionState, targetPosition, requestRevision, null);
	}

	private boolean publishPlaybackTransition(PlayableItem target, @Nullable PlayableItem current, int transitionState, long targetPosition, long requestRevision, @Nullable PlaybackEngineLease.Accepted accepted) {
		target = PlayableItemResolver.unwrap(target); if (current != null) current = PlayableItemResolver.unwrap(current);
		if ((accepted != null) && !playbackEngineLease.isCurrent(accepted)) return false;
		PlaybackStateCompat previousState = getPlaybackState();
		PlaybackPreparedItemDecisions.TransitionPublication publication =
				PlaybackPreparedItemDecisions.transitionPublication(target, current, transitionState,
						targetPosition, previousState,
						(playbackSnapshot == null) ? null : playbackSnapshot.getMetadata());
		boolean transportCommand = publication.transportCommand();

		if (!transportCommand && (target != current)) {
			preparationStatus.clear(); playbackTransition.begin(target, playbackSnapshot, targetPosition);
			metadata = null; session.setMetadata(null);
		}

		if ((accepted != null) && !playbackEngineLease.isCurrent(accepted)) return false;
		setPlaybackState(publication.state(), publication.item(), publication.metadata());
		if ((accepted != null) && !playbackEngineLease.isCurrent(accepted)) return false;
		if (!transportCommand) publishPreparationMetadata(target, requestRevision);
		return true;
	}

	private void publishPreparationMetadata(PlayableItem target, long requestRevision) {
		target.getMediaData().main().onSuccess(loaded -> handler.post(() -> {
			if (!isPlaybackRequestCurrent(requestRevision, target) ||
					!playbackTransition.isPending(target)) return;
			String targetName = target.getName();
			String progress = (metadata == null) ? null : metadata.getString(METADATA_KEY_DISPLAY_SUBTITLE);
			MediaMetadataCompat prepared = PlaybackPreparedItemDecisions.buildPreparationMetadata(loaded,
					targetName, progress);
			MediaEngine currentEngine = getEngine();
			if (currentEngine != null) {
				prepared = mergePreparationStatus(currentEngine, target, requestRevision, prepared);
			}
			setMetadata(prepared);
		}));
	}

	private void publishOutgoingPosition(@NonNull PlayableItem item, long position,
			@Nullable PlaybackSnapshot rollback) {
		PlaybackPreparedItemDecisions.OutgoingPublication publication =
				PlaybackPreparedItemDecisions.outgoingPublication(item, position, rollback,
						playbackSnapshot, getPlaybackState());
		metadata = publication.metadata();
		session.setMetadata(publication.metadata());
		setPlaybackState(publication.state(), item, publication.metadata());
	}

	static PlaybackStateCompat createPlaybackTransitionState(PlaybackStateCompat previousState,
			int transitionState, long position) {
		PlaybackStateCompat.Builder builder = (transitionState == STATE_CONNECTING) ?
				new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS) :
				new PlaybackStateCompat.Builder(previousState);
		float speed = (transitionState == STATE_CONNECTING) ? 1.0f : previousState.getPlaybackSpeed();
		return builder.setState(transitionState, Math.max(position, 0), speed).build();
	}

	private PlaybackStateCompat createPlayingState(PlayableItem i, boolean pause, long qid,
																								 long position, float speed) {
		return createPlayingState(i, pause ? STATE_PAUSED : PlaybackStateCompat.STATE_PLAYING, qid,
				position, speed);
	}

	private PlaybackStateCompat createPlayingState(PlayableItem i, int state, long qid,
																	 long position, float speed) {
		return playbackActions.createPlayingState(i, state, qid, position, speed,
				SUPPORTED_ACTIONS);
	}

	private void setPlaybackState(PlaybackStateCompat state) {
		MediaMetadataCompat metadata = (playbackSnapshot == null) ?
				null : playbackSnapshot.getMetadata();
		setPlaybackState(state, getCurrentItem(), metadata);
	}

	private void setPlaybackState(PlaybackStateCompat state, @Nullable PlayableItem item,
			@Nullable MediaMetadataCompat metadata) {
		int st = state.getState();
		if ((st != STATE_NONE) && (st != STATE_STOPPED)) session.setActive(true);
		PlaybackSnapshot previous = playbackSnapshot;
		PlaybackSnapshot current = updatePlaybackSnapshot(state, metadata, item);
		session.setPlaybackState(state);
		service.updateNotification(state.getState(), item);
		fireBroadcastEvent(l -> l.onPlaybackSnapshotChanged(this, previous, current));
		updateProgressPolicy(current);

		if (st == STATE_PLAYING) {
			MediaEngine engine = getEngine();
			if (engine == null) {
				playbackAdvanceWatchdog.cancel();
				return;
			}
			PlayableItem i = engine.getSource();
			if (i == null) {
				playbackAdvanceWatchdog.cancel();
				return;
			}
			if (i.isTimerRequired())
				playbackAdvanceWatchdog.arm(i, state.getPosition(), state.getPlaybackSpeed());
		} else {
			playbackAdvanceWatchdog.cancel();
		}
	}

	private PlaybackSnapshot updatePlaybackSnapshot(PlaybackStateCompat state,
			@Nullable MediaMetadataCompat metadata, @Nullable PlayableItem item) {
		PlaybackSnapshot current = new PlaybackSnapshot(++playbackSnapshotRevision,
				item, state, metadata, isCanonicalSnapshot(item));
		playbackSnapshot = current;
		return current;
	}

	private boolean isCanonicalSnapshot(@Nullable PlayableItem item) {
		MediaEngine currentEngine = getEngine();
		return (item != null) && (currentEngine != null) && playbackOwnership.ownsCommitted(
				currentEngine, PlayableItemResolver.unwrap(item));
	}

	@NonNull
	public PlaybackStateCompat getPlaybackState() {
		return playbackSnapshot.getState();
	}

	public boolean isPlaying() {
		return getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING;
	}

	private float getSpeed(PlayableItem i) {
		PreferenceStore prefs = i.getPrefs();

		if (prefs.hasPref(MediaPrefs.SPEED)) {
			return prefs.getFloatPref(MediaPrefs.SPEED);
		} else {
			prefs = i.getParent().getPrefs();
			return prefs.hasPref(MediaPrefs.SPEED) ? prefs.getFloatPref(MediaPrefs.SPEED) :
					getPlaybackControlPrefs().getFloatPref(MediaPrefs.SPEED);
		}
	}

	private void start(MediaEngine engine, float speed) {
		Log.i("Start playing ", engine.getSource().getId(), " with ",
				engine.getClass().getSimpleName());
		engine.setSpeed(speed);
		engine.start();
	}

	private void setAudiEffects(MediaEngine engine, PreferenceStore... stores) {
		AudioEffects ae = engine.getAudioEffects();
		if (ae == null) return;

		Equalizer eq = ae.getEqualizer();
		Virtualizer virt = ae.getVirtualizer();
		BassBoost bass = ae.getBassBoost();
		LoudnessEnhancer le = ae.getLoudnessEnhancer();

		for (PreferenceStore s : stores) {
			if (!s.getBooleanPref(AE_ENABLED)) continue;

			if (eq != null) {
				if (s.getBooleanPref(EQ_ENABLED)) {
					try {
						short num = eq.getNumberOfPresets();
						int p = s.getIntPref(EQ_PRESET);

						if ((p > 0) && (p <= num)) {
							eq.setEnabled(true);
							eq.usePreset((short) (p - 1));
						} else {
							int[] bands = null;

							if (p < 0) {
								String[] u = getPlaybackControlPrefs().getStringArrayPref(EQ_USER_PRESETS);
								if ((u.length > 0) && ((p = -p - 1) < u.length)) bands = getUserPresetBands(u[p]);
							} else {
								bands = s.getIntArrayPref(EQ_BANDS);
							}

							if (bands != null) {
								eq.setEnabled(true);

								for (short i = 0; (i < bands.length) && (i < num); i++) {
									eq.setBandLevel(i, (short) bands[i]);
								}
							} else {
								eq.setEnabled(false);
							}
						}
					} catch (Exception ex) {
						Log.e(ex, "Failed to configure Equalizer");
					}
				} else {
					eq.setEnabled(false);
				}
			}

			if (virt != null) {
				if (s.getBooleanPref(VIRT_ENABLED)) {
					try {
						virt.setEnabled(true);
						virt.setStrength((short) s.getIntPref(VIRT_STRENGTH));
						virt.forceVirtualizationMode(s.getIntPref(VIRT_MODE));
					} catch (Exception ex) {
						Log.e(ex, "Failed to configure Virtualizer");
					}
				} else {
					virt.setEnabled(false);
				}
			}

			if (bass != null) {
				if (bass.getStrengthSupported() && s.getBooleanPref(BASS_ENABLED)) {
					try {
						bass.setEnabled(true);
						bass.setStrength((short) s.getIntPref(BASS_STRENGTH));
					} catch (Exception ex) {
						Log.e(ex, "Failed to configure BassBoost");
					}
				} else {
					bass.setEnabled(false);
				}
			}

			if (le != null) {
				if (s.getBooleanPref(VOL_BOOST_ENABLED)) {
					try {
						le.setEnabled(true);
						le.setTargetGain(s.getIntPref(VOL_BOOST_STRENGTH) * 10);
					} catch (Exception ex) {
						Log.e(ex, "Failed to configure LoudnessEnhancer");
					}
				} else {
					le.setEnabled(false);
				}
			}

			return;
		}

		if (eq != null) eq.setEnabled(false);
		if (virt != null) virt.setEnabled(false);
		if (bass != null) bass.setEnabled(false);
		if (le != null) le.setEnabled(false);
	}

	private FutureSupplier<PlayableItem> prepareItem(PlayableItem i) {
		if (terminal || (i == null)) return completedNull();
		i = PlayableItemResolver.unwrap(i);
		PlayableItem target = i;
		if (target.isPlaybackTransportCommand()) return completed(target).main();

		FutureSupplier<Long> getDur = i.getDuration();

		// Make sure HTTP server is started
		if (target.isNetResource()) {
			FutureSupplier<NetServer> start = lib.getVfsManager().getNetServer();
			if (!start.isDone()) return start.and(getDur, (s, d) -> {
			}).map(v -> target).main();
		}

		if (!getDur.isDone()) return getDur.map(d -> target).timeout(5000, () -> target).main();
		return completed(target).main();
	}

	private void setLastPlayed(PlayableItem i, long position) {
		setLastPlayed(i, position, false);
	}

	private void setLastPlayed(PlayableItem i, long position, boolean committedOutgoing) {
		setLastPlayed(i, position, committedOutgoing, playbackRequestRevision);
	}

	private void setLastPlayed(PlayableItem i, long position, boolean committedOutgoing,
			long progressGeneration) {
		LastPlayedLease lease = captureLastPlayed(i);
		lib.getRecent().addItem(i);
		if (i.isExternal()) return;

		if (position < 0) {
			progressCoordinator.applyNegativeProgress(i, lease);
			return;
		}

		i.getDuration().main().onSuccess(dur -> progressCoordinator.applyResolvedProgress(i,
				position, dur, lease, committedOutgoing, progressGeneration));
	}

	static FutureSupplier<Void> persistPlaybackProgress(PlayableItem item, long position,
			long duration) {
		return PlaybackProgressCoordinator.persistPlaybackProgress(item, position, duration);
	}

	static FutureSupplier<Void> persistResolvedPlaybackProgress(PlayableItem item, long position,
			long duration, boolean ownsLastPlayed, boolean committedOutgoing) {
		return PlaybackProgressCoordinator.persistResolvedPlaybackProgress(item, position, duration,
				ownsLastPlayed, committedOutgoing);
	}

	private void updateProgressPolicy(@NonNull PlaybackSnapshot snapshot) {
		PlaybackStateCompat state = snapshot.getState();
		PlayableItem item = snapshot.getItem();
		boolean playing = state.getState() == STATE_PLAYING;
		progressCoordinator.cancelCheckpoint();
		if (!snapshot.isCanonical() || (item == null)) {
			progressPolicy.bind(null, -1L, false);
			return;
		}
		item = PlayableItemResolver.unwrap(item);
		long generation = playbackOwnership.committedGeneration(getEngine(), item);
		if (generation < 0L) {
			progressPolicy.bind(null, -1L, false);
			return;
		}
		if (!progressPolicy.bind(item, generation, playing) || !playing) return;
		progressCoordinator.scheduleProgressCheckpoint(item, generation, 0L);
	}

	private Bitmap defaultImage;

	private Bitmap getDefaultImage() {
		if (defaultImage == null) {
			try {
				var app = FermataApplication.get();
				var d = app.getPackageManager().getApplicationIcon(app.getPackageName());
				var res = app.getResources();
				int nw = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
				int nh = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
				int iw = d.getIntrinsicWidth();
				int ih = d.getIntrinsicHeight();
				float squeeze = Math.min(nw / Math.max(1f, iw), nh / Math.max(1f, ih)) * 0.8f;
				defaultImage = UiUtils.drawBitmap(d, Color.TRANSPARENT, Color.TRANSPARENT,
						0, 0, squeeze);
			} catch (Exception ex) {
				Log.e(ex, "Failed to get application icon");
			}
		}
		return defaultImage;
	}

	boolean isDefaultImage(Bitmap icon) {
		return (icon != null) && (defaultImage != null) && (icon.sameAs(defaultImage));
	}

	public interface Listener {
		default void onPlaybackSnapshotChanged(MediaSessionCallback cb,
				@Nullable PlaybackSnapshot previous, @NonNull PlaybackSnapshot current) {}

		default void onSubtitleStreamChanged(MediaSessionCallback cb,
																 @Nullable SubtitleStreamInfo info) {}

		default void onSubtitleLoadFailed(MediaSessionCallback cb, String message) {}
	}

	private static final class Prioritized<T> implements Comparable<Prioritized<T>> {
		final T obj;
		final int priority;

		Prioritized(T obj, int priority) {
			this.obj = obj;
			this.priority = priority;
		}

		@Override
		public int compareTo(Prioritized<T> o) {
			return Integer.compare(priority, o.priority);
		}

		@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
		@Override
		public boolean equals(Object obj) {
			return this.obj == ((Prioritized<?>) obj).obj;
		}

		@Override
		public int hashCode() {
			return obj.hashCode();
		}
	}

	public int getPlaybackTimer() {
		return playbackStopTimer.getRemainingSeconds();
	}

	public void setPlaybackTimer(int time) {
		playbackStopTimer.setSeconds(time);
	}
}
