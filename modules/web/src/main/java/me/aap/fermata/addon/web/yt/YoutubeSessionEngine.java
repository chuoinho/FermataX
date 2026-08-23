package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_YT;
import static me.aap.utils.async.Completed.completed;

import android.media.AudioManager;

import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;

import me.aap.fermata.addon.external.ExternalPlaybackDelegateItem;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.menu.OverlayMenu;

/** Stable MediaSession engine whose WebView host may be attached later or replaced. */
final class YoutubeSessionEngine implements MediaEngine {
	private final YoutubeAddon addon;
	private final YoutubeRuntime runtime;
	private final MediaSessionCallback callback;
	private PlayableItem source;
	private YoutubeItem descriptor;
	private YoutubeMediaEngine delegate;
	private long position;
	private float speed = 1f;
	private boolean positionSet;
	private boolean speedSet;
	private boolean playRequested;
	private boolean closed;

	YoutubeSessionEngine(YoutubeAddon addon, YoutubeRuntime runtime,
			MediaSessionCallback callback, YoutubeItem descriptor) {
		this.addon = addon;
		this.runtime = runtime;
		this.callback = callback;
		this.descriptor = descriptor;
	}

	boolean belongsTo(YoutubeAddon owner) {
		return addon == owner;
	}

	boolean owns(YoutubeMediaEngine candidate) {
		return !closed && (delegate == candidate);
	}

	@Nullable
	PlayableItem getExternalPlaybackOwner() {
		return (delegate == null) ? null : delegate.getExternalPlaybackOwner();
	}

	boolean attach(YoutubeMediaEngine next) {
		if (closed || (next == null) || !next.belongsTo(addon)) return false;
		if (delegate == next) return true;
		YoutubeMediaEngine previous = delegate;
		delegate = next;
		next.attachSession(this);
		if (previous != null) {
			previous.detachSession(this);
			previous.pause();
		}
		PlayableItem item = source;
		if (item != null) {
			next.prepare(item);
			if (positionSet) next.setPosition(position);
			if (speedSet) next.setSpeed(speed);
			if (playRequested || callback.isPlaying()) next.start();
		}
		return true;
	}

	void onDelegateDestroyed(YoutubeMediaEngine owner) {
		if (delegate != owner) return;
		position = Math.max(0L, callback.getPlaybackState().getPosition());
		float currentSpeed = callback.getPlaybackState().getPlaybackSpeed();
		if (currentSpeed > 0f) speed = currentSpeed;
		positionSet = true;
		speedSet = true;
		delegate = null;
		owner.detachSession(this);
		runtime.requestHost(this);
	}

	@Override
	public int getId() {
		return MEDIA_ENG_YT;
	}

	@Override
	public void prepare(PlayableItem requested) {
		if (closed) return;
		if (requested.isPlaybackTransportCommand()) {
			YoutubeMediaEngine current = delegate;
			if (current != null) current.prepare(requested);
			else runtime.requestHost(this);
			return;
		}
		PlayableItem resolved = PlayableItemResolver.unwrap(requested);
		YoutubeDescriptorItem item = descriptorItem(resolved);
		YoutubeItem nextDescriptor = null;
		if (item != null) {
			nextDescriptor = item.getYoutubeDescriptor();
		}
		setAuthoritativeSource(resolved, nextDescriptor);
		playRequested = false;
		YoutubeMediaEngine current = delegate;
		if (current != null) current.prepare(resolved);
		else runtime.requestHost(this);
	}

	boolean activate(YoutubePlaybackActivation activation) {
		if (closed || (delegate != activation.origin()) ||
				!activation.origin().isCurrentActivation(activation)) return false;

		PlayableItem previousSource = source;
		YoutubeItem previousDescriptor = descriptor;
		long previousPosition = position;
		boolean previousPositionSet = positionSet;
		PlayableItem externalOwner = activation.origin().getExternalPlaybackOwner();
		PlayableItem context = (previousSource == null) ? activation.origin().getSource() :
				previousSource;
		PlayableItem candidate = YoutubePlaybackActivation.selectSource(activation.videoId(),
				previousSource, externalOwner,
				() -> addon.createCanonicalPlaybackItem(activation.descriptor(), context));
		if (candidate == null) return false;

		YoutubeItem nextDescriptor = ((previousDescriptor != null) &&
				previousDescriptor.videoId().equals(activation.videoId())) ?
				YoutubeAddon.mergeYoutubeItem(previousDescriptor, activation.descriptor()) :
				activation.descriptor();
		setAuthoritativeSource(candidate, nextDescriptor);
		if (candidate instanceof YoutubeAddon.YoutubeHistoryItem history)
			history.updateDescriptor(nextDescriptor);

		boolean started = callback.startExternalPlayback(this);
		if (!started && !closed) {
			// Audio-focus failure closes the accepted session. A live session was rejected
			// before ownership changed and can safely restore its previous source.
			source = previousSource;
			descriptor = previousDescriptor;
			position = previousPosition;
			positionSet = previousPositionSet;
			return false;
		}
		return started;
	}

	boolean ownsVideo(String videoId) {
		return YoutubePlaybackActivation.matchesVideo(source, videoId);
	}

	private void setAuthoritativeSource(PlayableItem nextSource,
			@Nullable YoutubeItem nextDescriptor) {
		boolean identityChanged = (source != null) && (nextDescriptor != null) &&
				!YoutubePlaybackActivation.matchesVideo(source, nextDescriptor.videoId());
		source = nextSource;
		descriptor = nextDescriptor;
		if (identityChanged) {
			position = 0L;
			positionSet = false;
		}
	}

	@Nullable
	private static YoutubeDescriptorItem descriptorItem(@Nullable PlayableItem source) {
		if (source instanceof YoutubeDescriptorItem item) return item;
		if (source instanceof ExternalPlaybackDelegateItem external) {
			PlayableItem delegate = PlayableItemResolver.unwrap(external.getExternalPlaybackDelegate());
			if (delegate instanceof YoutubeDescriptorItem item) return item;
		}
		return null;
	}

	@Override
	public void start() {
		playRequested = true;
		YoutubeMediaEngine current = delegate;
		if (current != null) current.start();
		else runtime.requestHost(this);
	}

	@Override
	public void stop() {
		playRequested = false;
		if (delegate != null) delegate.stop();
	}

	@Override
	public void pause() {
		playRequested = false;
		if (delegate != null) delegate.pause();
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		YoutubeMediaEngine current = delegate;
		return (current == null) ? completed((descriptor == null) ? 0L :
				descriptor.durationMillis()) : current.getDuration();
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		YoutubeMediaEngine current = delegate;
		return (current == null) ? completed(position) : current.getPosition();
	}

	@Override
	public void setPosition(long position) {
		this.position = Math.max(0L, position);
		positionSet = true;
		if (delegate != null) delegate.setPosition(position);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		YoutubeMediaEngine current = delegate;
		return (current == null) ? completed(speed) : current.getSpeed();
	}

	@Override
	public void setSpeed(float speed) {
		this.speed = (speed > 0f) ? speed : 1f;
		speedSet = true;
		if (delegate != null) delegate.setSpeed(speed);
	}

	@Override
	public void setVideoView(@Nullable VideoView view) {
		if (delegate != null) delegate.setVideoView(view);
	}

	@Override
	public float getVideoWidth() {
		return (delegate == null) ? 0f : delegate.getVideoWidth();
	}

	@Override
	public float getVideoHeight() {
		return (delegate == null) ? 0f : delegate.getVideoHeight();
	}

	@Override
	public boolean isSplitModeSupported() {
		return (delegate != null) && delegate.isSplitModeSupported();
	}

	@Override
	public boolean hasVideoMenu() {
		return (delegate != null) && delegate.hasVideoMenu();
	}

	@Override
	public void contributeToMenu(OverlayMenu.Builder builder) {
		if (delegate != null) delegate.contributeToMenu(builder);
	}

	@Override
	public boolean requestAudioFocus(@Nullable AudioManager audioManager,
			@Nullable AudioFocusRequestCompat request) {
		return (delegate == null) || delegate.requestAudioFocus(audioManager, request);
	}

	@Override
	public void releaseAudioFocus(@Nullable AudioManager audioManager,
			@Nullable AudioFocusRequestCompat request) {
		if (delegate != null) delegate.releaseAudioFocus(audioManager, request);
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		playRequested = false;
		YoutubeMediaEngine current = delegate;
		delegate = null;
		runtime.release(this);
		if (current != null) {
			current.detachSession(this);
			current.close();
		}
	}
}
