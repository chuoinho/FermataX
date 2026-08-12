package me.aap.fermata.engine.vlc;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static java.util.Collections.emptyList;
import static me.aap.fermata.media.pref.MediaPrefs.HW_ACCEL_DECODING;
import static me.aap.fermata.media.pref.MediaPrefs.HW_ACCEL_DISABLED;
import static me.aap.fermata.media.pref.MediaPrefs.HW_ACCEL_FULL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.MediaPlayer.TrackDescription;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IMedia.AudioTrack;
import org.videolan.libvlc.interfaces.IMedia.SubtitleTrack;
import org.videolan.libvlc.interfaces.IMedia.VideoTrack;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.AudioStreamInfo;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineBase;
import me.aap.fermata.media.engine.MediaEngineException;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.fermata.media.net.ResolvedRemotePlaybackRequest;
import me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability;
import me.aap.fermata.media.net.UnsupportedPlaybackRequestException;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.policy.VideoSurfaceLayoutPolicy;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class VlcEngine extends MediaEngineBase
		implements MediaPlayer.EventListener, IVLCVout.OnNewVideoLayoutListener {
	@SuppressWarnings({"FieldCanBeLocal", "unused"}) // Hold reference to prevent garbage collection
	private final VlcEngineProvider provider;
	private final LibVLC vlc;
	private final MediaPlayer player;
	private final AudioEffects effects;
	@NonNull
	private Source source = Source.NULL;
	private long pendingPosition = -1;
	private FutureSupplier<RemotePlaybackRequest> remotePrepare;
	private RemotePlaybackRequest remoteRequest;
	private long prepareGeneration;
	private boolean nativeSubtitleSurface = true;
	private boolean firstVideoOutputReported;

	private void recordDiagnostic(String name, DiagnosticScope scope, DiagnosticPriority priority,
			@Nullable PlayableItem item, long generation, @Nullable String reason,
			@Nullable String errorClass) {
		try {
			DiagnosticEvent.Builder event = DiagnosticEvent.builder("engine_vlc", name)
					.scope(scope).priority(priority)
					.put("engine_id", MediaPrefs.MEDIA_ENG_VLC)
					.put("engine_class", getClass().getSimpleName())
					.put("generation", generation)
					.put("item_class", (item == null) ? "none" : item.getClass().getSimpleName())
					.put("item_fingerprint", (item == null) ? 0 : System.identityHashCode(item))
					.put("remote", item instanceof RemotePlaybackItem);
			if (reason != null) event.put("reason", reason);
			if (errorClass != null) event.put("error_class", errorClass);
			FermataApplication.get().getDiagnostics().record(event.build());
		} catch (Throwable ignored) {
			// Diagnostics must never affect VLC callbacks or playback.
		}
	}

	public VlcEngine(VlcEngineProvider provider, Listener listener) {
		super(listener);
		LibVLC vlc = provider.getVlc();
		int sessionId = provider.getAudioSessionId();
		effects = (sessionId != AudioManager.ERROR) ? AudioEffects.create(0, sessionId) : null;
		this.provider = provider;
		this.vlc = vlc;
		player = new MediaPlayer(vlc);
		player.setEventListener(this);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_VLC;
	}

	@Override
	public void prepare(PlayableItem source) {
		firstVideoOutputReported = false;
		stopped(false);
		this.source.close();
		this.source = Source.NULL;
		releaseRemoteRequest();
		FutureSupplier<RemotePlaybackRequest> previous = remotePrepare;
		remotePrepare = null;
		if (previous != null) previous.cancel();
		long generation = ++prepareGeneration;
		recordDiagnostic("prepare_started", DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE,
				source, generation, null, null);

		if (source instanceof RemotePlaybackItem remote) {
			this.source = new Source(source, null);
			if (!provider.supportsPlayback(source)) {
				recordDiagnostic("prepare_rejected", DiagnosticScope.ESSENTIAL,
						DiagnosticPriority.WARN, source, generation, "unsupported_profile", null);
				listener.onEngineError(this, new UnsupportedPlaybackRequestException(
						"VLC cannot enforce this remote playback request profile"));
				return;
			}
			try {
				remotePrepare = remote.prepareRemotePlayback(
						progress -> listener.onEnginePreparing(this, progress))
						.main().onCompletion((request, error) -> {
					if ((generation != prepareGeneration) || (this.source.getItem() != source)) {
						recordDiagnostic("prepare_callback_rejected", DiagnosticScope.DETAILED,
								DiagnosticPriority.DETAIL, source, generation, "stale_generation", null);
						if (request != null) request.close();
						return;
					}
					remotePrepare = null;
					if (error != null) {
						recordDiagnostic("engine_error", DiagnosticScope.ESSENTIAL,
								DiagnosticPriority.ERROR, source, generation, null,
								error.getClass().getSimpleName());
						listener.onEngineError(this, error);
					}
					else {
						try {
							remoteRequest = request;
							Set<EngineCapability> capabilities =
									provider.capabilitiesFor(request.getProfile());
							if (capabilities == null) throw new UnsupportedPlaybackRequestException(
									"VLC cannot enforce this remote playback request profile");
							ResolvedRemotePlaybackRequest resolved = request.resolve(
									System.currentTimeMillis(), capabilities);
							Map<String, String> headers = resolved.headersFor(resolved.getLocation());
							prepareResolved(source, Uri.parse(resolved.getLocation().toString()),
									headers.get("User-Agent"), headers);
						} catch (Throwable ex) {
							releaseRemoteRequest();
							listener.onEngineError(this, ex);
						}
					}
				});
			} catch (Throwable ex) {
				recordDiagnostic("engine_error", DiagnosticScope.ESSENTIAL,
						DiagnosticPriority.ERROR, source, generation, null, ex.getClass().getSimpleName());
				listener.onEngineError(this, ex);
			}
			return;
		}

		prepareResolved(source, source.getLocation(), source.getUserAgent(),
				source.getRequestHeaders());
	}

	private void prepareResolved(PlayableItem source, Uri uri, String userAgent,
			Map<String, String> requestHeaders) {
		Media media = null;
		ParcelFileDescriptor fd = null;

		try {
			String scheme = uri.getScheme();

			if ("content".equals(scheme)) {
				ContentResolver cr = vlc.getAppContext().getContentResolver();
				fd = cr.openFileDescriptor(uri, "r");
				media = (fd != null) ? new Media(vlc, fd.getFileDescriptor()) : new Media(vlc, uri);
			} else {
				media = new Media(vlc, uri);

				if ((scheme != null) && scheme.startsWith("http")) {
					String agent = userAgent;
					if (agent != null) media.addOption(":http-user-agent='" + agent + "'");
					String authorization = requestHeaders.get("Authorization");
					if ((authorization != null) && authorization.regionMatches(true, 0,
							"Basic ", 0, 6)) {
						try {
							String value = new String(Base64.getDecoder().decode(
									authorization.substring(6).trim()), StandardCharsets.UTF_8);
							int separator = value.indexOf(':');
							if (separator != -1) {
								media.addOption(":http-user=" + value.substring(0, separator));
								media.addOption(":http-pwd=" + value.substring(separator + 1));
							}
						} catch (IllegalArgumentException ignored) {
						}
					}
				}
			}

			media.addOption(":input-fast-seek");
			switch (source.getPrefs().getHwAccelPref()) {
				case HW_ACCEL_DECODING -> {
					media.setHWDecoderEnabled(true, true);
					media.addOption(":no-mediacodec-dr");
					media.addOption(":no-omxil-dr");
				}
				case HW_ACCEL_FULL -> media.setHWDecoderEnabled(true, true);
				case HW_ACCEL_DISABLED -> media.setHWDecoderEnabled(false, false);
			}

			PendingSource pending = new PendingSource(source, media, fd);
			this.source = pending;

			if (media.isParsed()) {
				prepared(pending);
			} else {
				Media m = media;
				m.setEventListener(e -> {
					if (m.isParsed()) {
						m.setEventListener(null);
						prepared(pending);
					}
				});
				m.parseAsync();
			}
		} catch (Throwable ex) {
			IoUtils.close(fd);
			if (media != null) media.release();
			if (this.source == Source.NULL) this.source = new Source(source, null);
			else this.source.close();
			recordDiagnostic("engine_error", DiagnosticScope.ESSENTIAL, DiagnosticPriority.ERROR,
					source, prepareGeneration, null, ex.getClass().getSimpleName());
			listener.onEngineError(this, ex);
		}
	}

	private void prepared(PendingSource source) {
		if (source != this.source) {
			source.close();
			return;
		}

		IMedia media = source.getMedia();
		long off = source.getItem().getOffset();
		this.source = source.prepare();
		pendingPosition = -1;
		player.setMedia(media);
		source.release();
		if (off > 0) player.setTime(off);
		recordDiagnostic("prepared", DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE,
				source.getItem(), prepareGeneration, "ready", null);
		listener.onEnginePrepared(this);
	}

	@Override
	public void start() {
		player.play();
		recordDiagnostic("started", DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE,
				getSource(), prepareGeneration, null, null);
	}

	@Override
	public void stop() {
		PlayableItem previous = getSource();
		prepareGeneration++;
		releaseRemoteRequest();
		FutureSupplier<RemotePlaybackRequest> pending = remotePrepare;
		remotePrepare = null;
		if (pending != null) pending.cancel();
		stopped(false);
		pendingPosition = -1;
		player.stop();
		player.detachViews();
		source.close();
		source = Source.NULL;
		recordDiagnostic("stopped", DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE, previous,
				prepareGeneration, null, null);
	}

	private void releaseRemoteRequest() {
		RemotePlaybackRequest request = remoteRequest;
		remoteRequest = null;
		if (request != null) request.close();
	}

	@Override
	public void pause() {
		stopped(true);
		player.pause();
	}

	@Override
	public PlayableItem getSource() {
		return source.getItem();
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		if (!source.isSeekable()) return completed(0L);

		long dur = source.getDuration();

		if (dur <= 0) {
			if ((dur = player.getLength()) > 0) {
				source.setDuration(dur);
				return completed(dur);
			} else {
				return completed(0L);
			}
		}

		return completed(dur);
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		long pos = pos();
		syncSub(pos, player.getRate(), false);
		return completed(pos);
	}

	@Override
	protected FutureSupplier<Long> getSubtitlePosition() {
		return completed(pos());
	}

	private long pos() {
		Source src = source;
		if ((src == Source.NULL) || !src.isSeekable()) return 0L;
		return ((pendingPosition == -1) ? player.getTime() : pendingPosition) -
				src.getItem().getOffset();
	}

	@Override
	public void setPosition(long position) {
		Source src = source;
		if (src == Source.NULL) return;

		long pos = src.getItem().getOffset() + position;
		if (isPlaying() || isPaused()) {
			player.setTime(pos);
			syncSub(position, player.getRate(), true);
		} else {
			pendingPosition = pos;
		}
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(player.getRate());
	}

	@Override
	public void setSpeed(float speed) {
		player.setRate(speed);
		syncSub(pos(), speed, true);
	}

	@Override
	public void setVideoView(@Nullable VideoView view) {
		super.setVideoView(view);
		attachVideoViews(view, nativeSubtitleSurface);
	}

	private void attachVideoViews(@Nullable VideoView view, boolean nativeSubtitles) {
		IVLCVout out = player.getVLCVout();
		out.detachViews();

		if (view != null) {
			out.setVideoView(view.getVideoSurface());
			SurfaceView subtitleSurface = view.getSubtitleSurface();
			if (nativeSubtitles && (subtitleSurface != null)) {
				out.setSubtitlesView(subtitleSurface);
			}
			out.attachViews(this);
			setSurfaceSize(view);
		}
		nativeSubtitleSurface = nativeSubtitles;
	}

	private void useNativeSubtitleSurface(boolean nativeSubtitles) {
		if (nativeSubtitleSurface == nativeSubtitles) return;
		attachVideoViews(videoView, nativeSubtitles);
	}

	@Override
	public float getVideoWidth() {
		float w = source.getVideoWidth();
		if ((int) w == 0) {
			VideoTrack t = player.getCurrentVideoTrack();
			if (t != null) return t.width;
		}
		return w;
	}

	@Override
	public float getVideoHeight() {
		float h = source.getVideoHeight();
		if ((int) h == 0) {
			VideoTrack t = player.getCurrentVideoTrack();
			if (t != null) return t.height;
		}
		return h;
	}

	@Override
	public AudioEffects getAudioEffects() {
		return effects;
	}

	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		if (source == Source.NULL) return emptyList();
		TrackDescription[] tracks = player.getAudioTracks();
		if ((tracks == null) || (tracks.length == 0)) return emptyList();
		IMedia m = player.getMedia();
		if (m == null) return emptyList();
		try {
			List<AudioStreamInfo> streams = new ArrayList<>(tracks.length);
			for (TrackDescription td : tracks) {
				if (td.id == -1) continue;
				IMedia.Track t = m.getTrack(td.id);
				if (!(t instanceof AudioTrack a)) continue;
				streams.add(new AudioStreamInfo(a.id, a.language, td.name));
			}
			return streams;
		} finally {
			m.release();
		}
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		int id = player.getAudioTrack();
		return CollectionUtils.find(getAudioStreamInfo(), s -> s.getId() == id);
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo i) {
		player.setAudioTrack((i != null) ? (int) i.getId() : -1);
	}

	@Override
	public boolean isAudioDelaySupported() {
		return true;
	}

	@Override
	public int getAudioDelay() {
		return (int) (player.getAudioDelay() / 1000);
	}

	@Override
	public void setAudioDelay(int milliseconds) {
		player.setAudioDelay(milliseconds * 1000L);
	}

	@Override
	public boolean isSubtitlesSupported() {
		if (super.isSubtitlesSupported()) return true;
		TrackDescription[] tracks = player.getSpuTracks();
		return (tracks != null) && (tracks.length != 0);
	}

	@Override
	public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		if (source == Source.NULL) return completedEmptyList();

		return super.getSubtitleStreamInfo().map(subFiles -> {
			TrackDescription[] tracks = player.getSpuTracks();
			if ((tracks == null) || (tracks.length == 0)) return subFiles;
			IMedia m = player.getMedia();
			if (m == null) return subFiles;
			try {
				List<SubtitleStreamInfo> streams = new ArrayList<>(subFiles.size() + tracks.length);
				streams.addAll(subFiles);
				for (TrackDescription td : tracks) {
					if (td.id == -1) continue;
					IMedia.Track t = m.getTrack(td.id);
					if (!(t instanceof SubtitleTrack s)) continue;
					streams.add(new SubtitleStreamInfo(s.id, s.language, td.name));
				}
				return streams;
			} finally {
				m.release();
			}
		});
	}

	@Nullable
	@Override
	public SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		var i = super.getCurrentSubtitleStreamInfo();
		if (i != null) return i;

		IMedia m = player.getMedia();
		if (m == null) return null;
		int id = player.getSpuTrack();
		if (id == -1) return null;
		TrackDescription[] tracks = player.getSpuTracks();
		if ((tracks == null) || (tracks.length == 0)) return null;

		for (TrackDescription td : tracks) {
			if (td.id != id) continue;
			IMedia.Track t = m.getTrack(id);
			if (!(t instanceof SubtitleTrack s)) return null;
			return new SubtitleStreamInfo(id, s.language, td.name);
		}

		return null;
	}

	@Override
	public void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {
		if (i == null) {
			player.setSpuTrack(-1);
			super.setCurrentSubtitleStream(null);
			useNativeSubtitleSurface(true);
			if (videoView != null) videoView.clearSubtitleSurface();
		} else if (i.getFiles().isEmpty()) {
			super.setCurrentSubtitleStream(null);
			useNativeSubtitleSurface(true);
			player.setSpuTrack((int) i.getId());
			if (videoView != null) videoView.revealSubtitleSurface();
		} else {
			player.setSpuTrack(-1);
			useNativeSubtitleSurface(false);
			super.setCurrentSubtitleStream(i);
		}
	}

	@Override
	public void setSubtitleDelay(int milliseconds) {
		super.setSubtitleDelay(milliseconds);
		player.setSpuDelay(milliseconds * 1000L);
	}

	@Override
	public void close() {
		stop();
		super.close();
		player.release();
		if (effects != null) effects.release();
	}

	@Override
	public void onEvent(MediaPlayer.Event event) {
		switch (event.type) {
			case MediaPlayer.Event.Buffering -> {
				float percent = event.getBuffering();
				if (percent == 100F) listener.onEngineBufferingCompleted(this);
				else listener.onEngineBuffering(this, (int) percent);
			}
			case MediaPlayer.Event.Playing -> {
				if (this.source instanceof VideoSource vs) {
					PlayableItemPrefs prefs = vs.getItem().getPrefs();
					MediaEngine.selectMediaStream(prefs::getAudioIdPref, prefs::getAudioLangPref,
							prefs::getAudioKeyPref, () -> completed(getAudioStreamInfo()),
							ai -> player.setAudioTrack((int) ai.getId()));

					if (BuildConfig.AUTO && (videoView != null)) {
						MainActivityDelegate.getActivityDelegate(videoView.getContext()).onSuccess(a -> {
							int delay = prefs.getAudioDelayPref(a.isCarActivity());
							if (delay != 0) player.setAudioDelay(delay * 1000L);
						});
					} else {
						int delay = prefs.getAudioDelayPref(false);
						if (delay != 0) player.setAudioDelay(delay * 1000L);
					}
				} else {
					player.setAudioDelay(0);
				}

				if (pendingPosition != -1) {
					player.setTime(pendingPosition);
					pendingPosition = -1;
				}

				if (!isPaused()) player.setSpuTrack(-1);
				started();
				recordDiagnostic("started", DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE,
						getSource(), prepareGeneration, "playing", null);
				listener.onEngineStarted(this);
				// VLC may reuse the same vout and omit onNewVideoLayout when two channels
				// share a resolution. Playing is the per-prepare fallback that releases the
				// transition mask after ownership has been committed.
				if (!firstVideoOutputReported && (source instanceof VideoSource)) {
					firstVideoOutputReported = true;
					recordDiagnostic("video_layout_ready", DiagnosticScope.ESSENTIAL,
							DiagnosticPriority.STATE, getSource(), prepareGeneration,
							"playing_fallback", null);
					listener.onVideoFirstFrame(this);
				}
			}
			case MediaPlayer.Event.EndReached -> {
				stopped(false);
				PlayableItem s = getSource();
				boolean stream = false;
				if (s != null) {
					if (s.isStream()) {
						stream = true;
					} else {
						String scheme = s.getLocation().getScheme();
						if ((scheme != null) && scheme.startsWith("http")) stream = true;
					}
				}
				if (stream) {
					float pos = player.getTime();
					float dur = player.getLength() * 0.9F;
					if ((dur > 0) && (pos < dur)) {
						// Failed to read the stream?
						Log.d("Position=", pos, " < duration=", dur);
						recordDiagnostic("engine_error", DiagnosticScope.ESSENTIAL,
								DiagnosticPriority.ERROR, s, prepareGeneration, "stream_read_failed",
								MediaEngineException.class.getSimpleName());
						listener.onEngineError(this, new MediaEngineException("Failed to read stream " + s));
						break;
					}
				}
				listener.onEngineEnded(this);
			}
			case MediaPlayer.Event.EncounteredError -> {
				recordDiagnostic("engine_error", DiagnosticScope.ESSENTIAL,
						DiagnosticPriority.ERROR, getSource(), prepareGeneration, "vlc_event_error",
						MediaEngineException.class.getSimpleName());
				listener.onEngineError(this, new MediaEngineException(""));
			}
		}
	}

	@Override
	public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth,
															 int visibleHeight, int sarNum, int sarDen) {
		if ((videoView == null) || !(source instanceof VideoSource src)) return;
		if (!firstVideoOutputReported && (visibleWidth > 0) && (visibleHeight > 0)) {
			firstVideoOutputReported = true;
			recordDiagnostic("video_layout_ready", DiagnosticScope.ESSENTIAL,
					DiagnosticPriority.STATE, getSource(), prepareGeneration, "layout_callback", null);
			listener.onVideoFirstFrame(this);
		}
		src.videoWidth = width;
		src.videoHeight = height;
		src.visibleVideoWidth = visibleWidth;
		src.visibleVideoHeight = visibleHeight;
		src.videoSarNum = sarNum;
		src.videoSarDen = sarDen;
		setSurfaceSize(videoView, src);
	}

	@Override
	public boolean setSurfaceSize(VideoView view) {
		if (source instanceof VideoSource) setSurfaceSize(view, (VideoSource) source);
		return true;
	}

	private void setSurfaceSize(VideoView view, VideoSource src) {
		int sw = view.getWidth();
		int sh = view.getHeight();
		if ((sw == 0) || (sh == 0)) return;

		int scaleType = view.usesAutomotivePresentation() ? SCALE_BEST :
				src.getItem().getPrefs().getVideoScalePref();
		player.getVLCVout().setWindowSize(sw, sh);

		if ((src.videoWidth == 0) || (src.videoHeight == 0)) {
			setPlayerLayout(sw, sh, scaleType);
			float videoWidth = 0f;
			float videoHeight = 0f;
			float pixelRatio = 1f;
			VideoTrack track = player.getCurrentVideoTrack();
			if (track != null) {
				videoWidth = track.width;
				videoHeight = track.height;
				boolean swap = track.orientation == VideoTrack.Orientation.LeftBottom ||
						track.orientation == VideoTrack.Orientation.RightTop;
				if (swap) {
					float width = videoWidth;
					videoWidth = videoHeight;
					videoHeight = width;
				}
				if ((track.sarNum > 0) && (track.sarDen > 0))
					pixelRatio = (float) track.sarNum / track.sarDen;
			}
			VideoSurfaceLayoutPolicy.Size fallback = VideoSurfaceLayoutPolicy.resolve(
					sw, sh, videoWidth, videoHeight, scaleType, pixelRatio);
			setSurfaceLayout(view, fallback.width(), fallback.height());
			return;
		}

		ViewGroup.LayoutParams lp = view.getVideoSurface().getLayoutParams();

		if ((lp.width == MATCH_PARENT) && (lp.height == MATCH_PARENT)) {
			player.setScale(0);
			player.setAspectRatio(null);
		}

		double dw = sw;
		double dh = sh;
		double ar;
		double vw;

		if (src.videoSarDen == src.videoSarNum) {
			vw = src.visibleVideoWidth;
			ar = (double) src.visibleVideoWidth / (double) src.visibleVideoHeight;
		} else {
			vw = src.visibleVideoWidth * ((double) src.videoSarNum / (double) src.videoSarDen);
			ar = vw / src.visibleVideoHeight;
		}

		double dar = dw / dh;

		switch (scaleType) {
			default:
			case SCALE_BEST:
				if (dar < ar) dh = dw / ar;
				else dw = dh * ar;
				break;
			case SCALE_FILL:
				if (dar >= ar) dh = dw / ar;
				else dw = dh * ar;
				break;
			case SCALE_ORIGINAL:
				dh = src.videoHeight;
				dw = vw;
				break;
			case SCALE_4_3:
				ar = 4.0 / 3.0;
				if (dar < ar) dh = dw / ar;
				else dw = dh * ar;
				break;
			case SCALE_16_9:
				ar = 16.0 / 9.0;
				if (dar < ar) dh = dw / ar;
				else dw = dh * ar;
				break;
		}

		sw = (int) Math.ceil(dw * src.videoWidth / src.visibleVideoWidth);
		sh = (int) Math.ceil(dh * src.videoHeight / src.visibleVideoHeight);
		setSurfaceLayout(view, sw, sh);
	}

	private void setPlayerLayout(int surfaceW, int surfaceH, int scaleType) {
		switch (scaleType) {
			case SCALE_BEST -> {
				player.setScale(0);
				player.setAspectRatio(null);
			}
			case SCALE_FILL -> {
				VideoTrack t = player.getCurrentVideoTrack();
				if (t == null) {
					player.setScale(0);
					player.setAspectRatio(null);
					break;
				}
				float videoW = t.width;
				float videoH = t.height;
				boolean swap = t.orientation == VideoTrack.Orientation.LeftBottom ||
						t.orientation == VideoTrack.Orientation.RightTop;
				if (swap) {
					float w = videoW;
					videoW = videoH;
					videoH = w;
				}
				if (t.sarNum != t.sarDen) videoW = videoW * t.sarNum / t.sarDen;
				float ar = videoW / videoH;
				float dar = (float) surfaceW / surfaceH;
				float scale;
				if (dar >= ar) scale = surfaceW / videoW;
				else scale = surfaceH / videoH;
				player.setScale(scale);
				player.setAspectRatio(null);
			}
			case SCALE_ORIGINAL -> {
				player.setScale(1);
				player.setAspectRatio(null);
			}
			case SCALE_4_3 -> {
				player.setScale(0);
				player.setAspectRatio("4:3");
			}
			case SCALE_16_9 -> {
				player.setScale(0);
				player.setAspectRatio("16:9");
			}
		}
	}

	private void setSurfaceLayout(VideoView view, int width, int height) {
		SurfaceView surface = view.getVideoSurface();
		ViewGroup.LayoutParams lp = surface.getLayoutParams();

		if ((lp.width != width) || (lp.height != height)) {
			lp.width = width;
			lp.height = height;
			surface.setLayoutParams(lp);
		}

		if ((surface = view.getSubtitleSurface()) != null) {
			lp = surface.getLayoutParams();

			if ((lp.width != width) || (lp.height != height)) {
				lp.width = width;
				lp.height = height;
				surface.setLayoutParams(lp);
			}
		}
	}

	@Override
	public void mute(Context ctx) {
		player.setVolume(0);
	}

	@Override
	public void unmute(Context ctx) {
		player.setVolume(100);
	}

	private static class Source implements Closeable {
		private static final Source NULL = new Source(null, null);
		private final PlayableItem item;
		ParcelFileDescriptor fd;

		Source(PlayableItem item, ParcelFileDescriptor fd) {
			this.item = item;
			this.fd = fd;
		}

		PlayableItem getItem() {
			return item;
		}

		long getDuration() {
			return 0;
		}

		boolean isSeekable() {
			return false;
		}

		void setDuration(long duration) {
		}

		int getVideoWidth() {
			return 0;
		}

		int getVideoHeight() {
			return 0;
		}

		@Override
		@CallSuper
		public void close() {
			if (fd != null) {
				IoUtils.close(fd);
				fd = null;
			}
		}

		@NonNull
		@Override
		public String toString() {
			return String.valueOf(getItem());
		}
	}

	private static class PendingSource extends Source {
		IMedia media;

		public PendingSource(PlayableItem item, IMedia media, ParcelFileDescriptor fd) {
			super(item, fd);
			this.media = media;
		}

		IMedia getMedia() {
			return media;
		}

		PreparedSource prepare() {
			PlayableItem pi = getItem();
			boolean seekable = pi.isSeekable();
			long dur = media.getDuration();

			if (dur == -1) {
				Long itemDur = getItem().getDuration().peek();
				if (itemDur != null) dur = itemDur;
			}

			if (pi.isVideo()) {
				return new VideoSource(pi, fd, dur, seekable);
			} else {
				return new PreparedSource(pi, fd, dur, seekable);
			}
		}

		public void close() {
			super.close();
			release();
		}

		void release() {
			if (media != null) {
				media.release();
				media = null;
			}
		}
	}

	private static class PreparedSource extends Source {
		private long duration;
		private final boolean seekable;

		PreparedSource(PlayableItem item, ParcelFileDescriptor fd, long duration, boolean seekable) {
			super(item, fd);
			this.duration = duration;
			this.seekable = seekable;
		}

		@Override
		long getDuration() {
			return duration;
		}

		@Override
		public boolean isSeekable() {
			return seekable;
		}

		@Override
		void setDuration(long duration) {
			this.duration = duration;
		}
	}

	private static final class VideoSource extends PreparedSource {
		int videoWidth;
		int videoHeight;
		int visibleVideoWidth;
		int visibleVideoHeight;
		int videoSarNum;
		int videoSarDen;

		VideoSource(PlayableItem item, ParcelFileDescriptor fd, long duration, boolean seekable) {
			super(item, fd, duration, seekable);
		}

		@Override
		int getVideoWidth() {
			return videoWidth;
		}

		@Override
		int getVideoHeight() {
			return videoHeight;
		}
	}
}
