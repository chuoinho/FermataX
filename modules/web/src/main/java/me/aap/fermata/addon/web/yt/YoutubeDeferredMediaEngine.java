package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_YT;
import static me.aap.utils.async.Completed.completed;

import android.media.AudioManager;

import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.addon.external.ExternalPlaybackDelegateItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * Keeps a YouTube media request alive until the Activity can host the WebView.
 *
 * <p>Android Auto may issue a media command while the app Activity does not exist yet. Returning
 * {@code null} from the item resolver makes the generic MediaPlayer try to play the YouTube HTML
 * page. This small handoff engine keeps the source and media-session request stable, then swaps
 * to the one engine owned by the real {@link YoutubeWebView} when the Activity is ready.</p>
 */
final class YoutubeDeferredMediaEngine implements MediaEngine {
	private final YoutubeAddon addon;
	private YoutubeItem descriptor;
	private final MediaSessionCallback callback;
	private PlayableItem source;
	private YoutubeMediaEngine delegate;
	private VideoView videoView;
	private long position;
	private float speed = 1f;
	private boolean positionSet;
	private boolean speedSet;
	private long handoffToken = -1L;
	private long attachmentGeneration;
	private boolean closed;

	YoutubeDeferredMediaEngine(YoutubeAddon addon, YoutubeItem descriptor,
														MediaSessionCallback callback) {
		this.addon = addon;
		this.descriptor = descriptor;
		this.callback = callback;
	}

	boolean isClosed() {
		return closed;
	}

	boolean isAttachmentCurrent(long generation) {
		return !closed && (generation == attachmentGeneration);
	}

	boolean belongsTo(YoutubeAddon owner) {
		return addon == owner;
	}

	void failAttachment(long generation) {
		if (!isAttachmentCurrent(generation)) return;
		if (callback.getEngine() == this) callback.onStop();
		else close();
	}

	boolean attach(YoutubeMediaEngine engine) {
		return attach(engine, attachmentGeneration);
	}

	boolean attach(YoutubeMediaEngine engine, long generation) {
		if (!isAttachmentCurrent(generation) || (engine == null) ||
				!engine.belongsTo(addon) || (source == null)) return false;
		if (!callback.handoffPendingEngine(this, engine, handoffToken)) {
			// Another item or addon won the race. Do not let this older request take ownership later.
			if (callback.getEngine() != this) closed = true;
			return false;
		}
		delegate = engine;
		closed = true;
		if (videoView != null) engine.setVideoView(videoView);
		engine.prepare(source);
		if (positionSet) engine.setPosition(position);
		if (speedSet) engine.setSpeed(speed);
		callback.onEngineMetadataChanged(engine);
		return true;
	}

	@Override
	public int getId() {
		return MEDIA_ENG_YT;
	}

	@Override
	public void prepare(PlayableItem source) {
		if (closed) return;
		PlayableItem resolved = PlayableItemResolver.unwrap(source);
		YoutubeDescriptorItem descriptorItem = descriptorItem(resolved);
		if (descriptorItem != null) {
			YoutubeItem next = descriptorItem.getYoutubeDescriptor();
			if ((descriptor == null) || !descriptor.videoId().equals(next.videoId())) position = 0L;
			descriptor = next;
		}
		this.source = resolved;
		handoffToken = callback.capturePendingEngineHandoff(this, resolved);
		long generation = ++attachmentGeneration;
		addon.attachHistoryEngine(this, generation);
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

	@Override
	public void start() {
		if (delegate != null) delegate.start();
	}

	@Override
	public void stop() {
		if (delegate != null) delegate.stop();
	}

	@Override
	public void pause() {
		if (delegate != null) delegate.pause();
	}

	@Override
	public PlayableItem getSource() {
		return (delegate == null) ? source : delegate.getSource();
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return (delegate == null) ? completed(descriptor.durationMillis()) : delegate.getDuration();
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return (delegate == null) ? completed(position) : delegate.getPosition();
	}

	@Override
	public void setPosition(long position) {
		this.position = Math.max(0L, position);
		positionSet = true;
		if (delegate != null) delegate.setPosition(position);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return (delegate == null) ? completed(speed) : delegate.getSpeed();
	}

	@Override
	public void setSpeed(float speed) {
		this.speed = (speed > 0f) ? speed : 1f;
		speedSet = true;
		if (delegate != null) delegate.setSpeed(speed);
	}

	@Override
	public void setVideoView(@Nullable VideoView view) {
		videoView = view;
		if (delegate != null) delegate.setVideoView(view);
	}

	@Override
	public float getVideoWidth() {
		return (delegate == null) ? 0 : delegate.getVideoWidth();
	}

	@Override
	public float getVideoHeight() {
		return (delegate == null) ? 0 : delegate.getVideoHeight();
	}

	@Override
	public boolean isSplitModeSupported() {
		return false;
	}

	@Override
	public boolean hasVideoMenu() {
		return delegate != null && delegate.hasVideoMenu();
	}

	@Override
	public void contributeToMenu(OverlayMenu.Builder builder) {
		if (delegate != null) delegate.contributeToMenu(builder);
	}

	@Override
	public boolean requestAudioFocus(@Nullable AudioManager audioManager,
																										 @Nullable AudioFocusRequestCompat audioFocusReq) {
		return (delegate == null) || delegate.requestAudioFocus(audioManager, audioFocusReq);
	}

	@Override
	public void releaseAudioFocus(@Nullable AudioManager audioManager,
																										 @Nullable AudioFocusRequestCompat audioFocusReq) {
			if (delegate != null) delegate.releaseAudioFocus(audioManager, audioFocusReq);
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		if (delegate != null) delegate.close();
	}
}
