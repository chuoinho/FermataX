package me.aap.fermata.addon.web;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_WEB;
import static me.aap.utils.async.Completed.completed;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.external.ExternalPlaybackRequest;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;

/** MediaSession owner for a transient page rendered by the Web addon. */
final class WebExternalMediaEngine implements MediaEngine {
	interface Host {
		void attachExternalPlayback(WebExternalMediaEngine engine);

		void detachExternalPlayback(WebExternalMediaEngine engine);
	}

	private final Host host;
	private final ExternalPlaybackRequest request;
	private final Listener listener;
	private PlayableItem source;
	private boolean prepared;
	private boolean started;
	private boolean closed;

	WebExternalMediaEngine(Host host, ExternalPlaybackRequest request,
			Listener listener) {
		this.host = host;
		this.request = request;
		this.listener = listener;
	}

	boolean belongsTo(WebBrowserAddon owner) {
		return host == owner;
	}

	ExternalPlaybackRequest getRequest() {
		return request;
	}

	boolean attach(WebBrowserFragment fragment) {
		if (closed || (source == null) || !fragment.openExternalPlayback(this))
			return false;
		if (prepared) return true;
		prepared = true;
		listener.onEnginePrepared(this);
		return true;
	}

	@Override
	public int getId() {
		return MEDIA_ENG_WEB;
	}

	@Override
	public void prepare(PlayableItem source) {
		if (closed) return;
		this.source = source;
		host.attachExternalPlayback(this);
	}

	@Override
	public void start() {
		if (closed || !prepared || started) return;
		started = true;
		listener.onEngineStarted(this);
	}

	@Override
	public void stop() {
		started = false;
	}

	@Override
	public void pause() {
		started = false;
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return completed(request.getDurationMillis());
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return completed(0L);
	}

	@Override
	public void setPosition(long position) {
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(1f);
	}

	@Override
	public void setSpeed(float speed) {
	}

	@Override
	public void setVideoView(@Nullable VideoView view) {
	}

	@Override
	public float getVideoWidth() {
		return 0f;
	}

	@Override
	public float getVideoHeight() {
		return 0f;
	}

	@Override
	public boolean isSplitModeSupported() {
		return false;
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		started = false;
		host.detachExternalPlayback(this);
		request.close();
	}
}
