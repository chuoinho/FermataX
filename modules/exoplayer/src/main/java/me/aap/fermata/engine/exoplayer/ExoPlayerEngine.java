package me.aap.fermata.engine.exoplayer;

import static me.aap.utils.async.Completed.completed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.datasource.cronet.CronetDataSource;
import androidx.media3.datasource.cronet.CronetUtil;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;

import org.chromium.net.CronetEngine;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.AudioStreamInfo;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineBase;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.ResolvedRemotePlaybackRequest;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
@UnstableApi
public class ExoPlayerEngine extends MediaEngineBase implements Player.Listener {
	private static final DataSource.Factory httpDsFactory;

	static {
		httpDsFactory = createHttpDataSourceFactory();
	}

	private static DataSource.Factory createHttpDataSourceFactory() {
		try {
			CronetEngine cre = CronetUtil.buildCronetEngine(FermataApplication.get(),
					"Fermata/" + BuildConfig.VERSION_NAME, true);
			if (cre != null) {
				return new CronetDataSource.Factory(cre, Executors.newSingleThreadExecutor());
			}
		} catch (Throwable err) {
			Log.w(err, "Cronet is unavailable. Falling back to the default HTTP data source.");
		}

		CookieManager cookieManager = new CookieManager();
		cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
		CookieHandler.setDefault(cookieManager);
		return new DefaultHttpDataSource.Factory();
	}

	private final Context context;
	private final Timeline.Period period = new Timeline.Period();
	private final ExoPlayer player;
	private final AudioEffects audioEffects;
	private volatile PlayableItem source;
	private FutureSupplier<RemotePlaybackRequest> remotePrepare;
	private RemotePlaybackRequest remoteRequest;
	private long prepareGeneration;
	private boolean preparing;
	private boolean buffering;
	private boolean isHls;

	public ExoPlayerEngine(Context ctx, Listener listener) {
		super(listener);
		context = ctx;
		DefaultDataSource.Factory dsFactory = new DefaultDataSource.Factory(ctx, httpDsFactory);
		MediaSource.Factory msFactory =
				new DefaultMediaSourceFactory(ctx).setDataSourceFactory(dsFactory);
		player = new ExoPlayer.Builder(ctx, new DefaultRenderersFactory(ctx) {
			{
				setEnableDecoderFallback(true);
				setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
			}

			@Override
			protected AudioSink buildAudioSink(@NonNull Context context,
					boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
				return new DefaultAudioSink.Builder(ctx)
						.setAudioTrackBufferSizeProvider(
								new DefaultAudioTrackBufferSizeProvider.Builder()
										.setMaxPcmBufferDurationUs(5_000_000)
										.setPcmBufferMultiplicationFactor(16)
										.setOffloadBufferDurationUs(120_000_000).build())
						.build();
			}
		}).setMediaSourceFactory(msFactory).build();
		player.addListener(this);
		audioEffects = AudioEffects.create(0, player.getAudioSessionId());
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_EXO;
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void prepare(PlayableItem source) {
		if (this.source == null) stopped(false);
		else stop();
		this.source = source;
		preparing = true;
		buffering = false;
		long generation = ++prepareGeneration;

		if (source instanceof RemotePlaybackItem remote) {
			if (!ExoPlayerEngineProvider.playbackCapabilities().containsAll(
					remote.getPlaybackRequestProfile().getRequiredEngineCapabilities())) {
				playbackRequestFailed(source, generation,
						new IllegalArgumentException("Unsupported playback request profile"));
				return;
			}
			try {
				remotePrepare = remote.prepareRemotePlayback(
						progress -> listener.onEnginePreparing(this, progress))
						.main().onCompletion((request, error) -> {
					if ((generation != prepareGeneration) || (this.source != source)) {
						if (request != null) request.close();
						return;
					}
					remotePrepare = null;
					if (error != null) playbackRequestFailed(source, generation, error);
					else prepareSource(source, request);
				});
			} catch (Throwable ex) {
				playbackRequestFailed(source, generation, ex);
			}
			return;
		}

		prepareSource(source, null);
	}

	private void prepareSource(PlayableItem source, @Nullable RemotePlaybackRequest request) {
		remoteRequest = request;
		Uri uri = (request == null) ? source.getLocation()
				: Uri.parse(request.getLocation().toString());
		MediaItem m = MediaItem.fromUri(uri);
		isHls = Util.inferContentType(uri) == C.CONTENT_TYPE_HLS;
		if (request != null) {
			ResolvedRemotePlaybackRequest resolved;
			try {
				resolved = request.resolve(System.currentTimeMillis(),
						ExoPlayerEngineProvider.playbackCapabilities());
			} catch (Throwable ex) {
				playbackRequestFailed(source, prepareGeneration, ex);
				return;
			}
			DataSource.Factory profileHttp = () -> new ProfileHttpDataSource(resolved);
			DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(context, profileHttp);
			DefaultMediaSourceFactory mediaSources = new DefaultMediaSourceFactory(context)
					.setDataSourceFactory(dataSource);
			if (resolved.getProfile().getRequiredEngineCapabilities().contains(
					PlaybackRequestProfile.EngineCapability.SINGLE_ATTEMPT_LOADING)) {
				mediaSources.setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(0));
			}
			MediaSource mediaSource = mediaSources.createMediaSource(m);
			player.setMediaSource(mediaSource);
			player.prepare();
			return;
		}
		Map<String, String> headers = source.getRequestHeaders();
		if (headers.isEmpty()) {
			player.setMediaItem(m);
		} else {
			DataSource.Factory resolving = new ResolvingDataSource.Factory(httpDsFactory,
					dataSpec -> dataSpec.withAdditionalHeaders(headers));
			DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(context, resolving);
			MediaSource mediaSource = new DefaultMediaSourceFactory(context)
					.setDataSourceFactory(dataSource).createMediaSource(m);
			player.setMediaSource(mediaSource);
		}
		player.prepare();
	}

	private void playbackRequestFailed(PlayableItem source, long generation, Throwable error) {
		if ((generation != prepareGeneration) || (this.source != source)) return;
		releaseRemoteRequest();
		preparing = false;
		listener.onEngineError(this, error);
	}

	@Override
	public void start() {
		player.setPlayWhenReady(true);
		listener.onEngineStarted(this);
		started();
	}

	@Override
	public void stop() {
		prepareGeneration++;
		releaseRemoteRequest();
		FutureSupplier<RemotePlaybackRequest> pending = remotePrepare;
		remotePrepare = null;
		if (pending != null) pending.cancel();
		stopped(false);
		player.stop();
		source = null;
	}

	private void releaseRemoteRequest() {
		RemotePlaybackRequest request = remoteRequest;
		remoteRequest = null;
		if (request != null) request.close();
	}

	@Override
	public void pause() {
		stopped(true);
		player.setPlayWhenReady(false);
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return completed(!isHls && (source != null) ? player.getDuration() : 0);
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		syncSub(false);
		return completed(pos());
	}

	@Override
	protected FutureSupplier<Long> getSubtitlePosition() {
		return completed(pos());
	}

	private long pos() {
		if (source == null) return 0L;
		var pos = player.getCurrentPosition();
		if (isHls) {
			var tl = player.getCurrentTimeline();
			if (!tl.isEmpty()) {
				pos -= tl.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs();
			}
		}
		return pos - source.getOffset();
	}

	protected long subSchedulerClock() {
		return pos();
	}

	void syncSub(boolean restart) {
		syncSub(subSchedulerClock(), speed(), restart);
	}

	@Override
	public void setPosition(long position) {
		if (source == null) return;
		var pos = source.getOffset() + position;
		player.seekTo(pos);
		syncSub(true);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(speed());
	}

	private float speed() {
		return player.getPlaybackParameters().speed;
	}

	@Override
	public void setSpeed(float speed) {
		player.setPlaybackParameters(new PlaybackParameters(speed));
		syncSub(true);
	}

	@Override
	public void setVideoView(VideoView view) {
		super.setVideoView(view);
		player.setVideoSurfaceHolder((view == null) ? null : view.getVideoSurface().getHolder());
	}

	@Override
	public float getVideoWidth() {
		Format f = player.getVideoFormat();
		return (f == null) ? 0 : f.width;
	}

	@Override
	public float getVideoHeight() {
		Format f = player.getVideoFormat();
		return (f == null) ? 0 : f.height;
	}

	@Override
	public float getVideoPixelWidthHeightRatio() {
		VideoSize size = player.getVideoSize();
		return (size == null) ? 1f : size.pixelWidthHeightRatio;
	}

	@Override
	public AudioEffects getAudioEffects() {
		return audioEffects;
	}

	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		var groups = player.getCurrentTracks().getGroups();
		var streams = new ArrayList<AudioStreamInfo>();
		for (int i = 0, n = groups.size(); i < n; i++) {
			var group = groups.get(i);
			if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
			for (int j = 0; j < group.length; j++) {
				var fmt = group.getTrackFormat(j);
				streams.add(new AudioStreamInfo(i * 1000L + j, fmt.language, fmt.label));
			}
		}
		return streams;
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		var groups = player.getCurrentTracks().getGroups();
		for (int i = 0, n = groups.size(); i < n; i++) {
			var group = groups.get(i);
			if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
			for (int j = 0; j < group.length; j++) {
				if (group.isTrackSelected(j)) {
					var fmt = group.getTrackFormat(j);
					return new AudioStreamInfo(i * 1000L + j, fmt.language, fmt.label);
				}
			}
		}
		return null;
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo info) {
		if (info == null) return;

		var groups = player.getCurrentTracks().getGroups();
		for (int i = 0, n = groups.size(); i < n; i++) {
			var group = groups.get(i);
			if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
			for (int j = 0; j < group.length; j++) {
				if (info.getId() != (i * 1000L + j)) continue;
				player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
						.setOverrideForType(new androidx.media3.common.TrackSelectionOverride(
								group.getMediaTrackGroup(), j)).build());
				return;
			}
		}
	}

	@Override
	public void close() {
		stop();
		super.close();
		player.removeListener(this);
		player.release();
		source = null;
		if (audioEffects != null) audioEffects.release();
	}

	@Override
	public void mute(Context ctx) {
		player.setVolume(0f);
	}

	@Override
	public void unmute(Context ctx) {
		player.setVolume(1f);
	}

	@Override
	public void onPlaybackStateChanged(int playbackState) {
		if (playbackState == Player.STATE_BUFFERING) {
			buffering = true;
			listener.onEngineBuffering(this, player.getBufferedPercentage());
		} else if (playbackState == Player.STATE_READY) {
			if (buffering) {
				buffering = false;
				listener.onEngineBufferingCompleted(this);
			}
			if (preparing) {
				preparing = false;
				long off = source.getOffset();
				if (off > 0) player.seekTo(off);
				listener.onEnginePrepared(this);
				var prefs = source.getPrefs();
				MediaEngine.selectMediaStream(prefs::getAudioIdPref, prefs::getAudioLangPref,
						prefs::getAudioKeyPref, () -> completed(getAudioStreamInfo()),
						this::setCurrentAudioStream);
			}
		} else if (playbackState == Player.STATE_ENDED) {
			stopped(false);
			listener.onEngineEnded(this);
		}
	}

	@Override
	public void onVideoSizeChanged(VideoSize videoSize) {
		listener.onVideoSizeChanged(this, videoSize.width, videoSize.height);
	}

	@Override
	public void onRenderedFirstFrame() {
		listener.onVideoFirstFrame(this);
	}

	@Override
	public void onPlayerError(@NonNull PlaybackException error) {
		listener.onEngineError(this, error);
	}

}
