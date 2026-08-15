package me.aap.fermata.ui.view;

import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY;
import static androidx.core.text.HtmlCompat.fromHtml;
import static me.aap.fermata.media.lib.MediaLib.PlayableItem;
import static me.aap.fermata.media.pref.MediaPrefs.SUB_SIZE;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.circularreveal.CircularRevealFrameLayout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.VideoOutputCoordinator;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.policy.VideoFormatSnapshot;
import me.aap.fermata.ui.policy.VideoRenderPlan;
import me.aap.fermata.ui.policy.VideoRenderPlanner;
import me.aap.fermata.ui.policy.VideoViewport;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public class VideoView extends FrameLayout
		implements SurfaceHolder.Callback, View.OnLayoutChangeListener, PreferenceStore.Listener,
		MainActivityListener, BiConsumer<SubGrid.Position, Subtitles.Text> {
	private static final long[] SURFACE_SIZE_RETRY_DELAYS = {150L, 500L, 1500L};
	private final Set<PreferenceStore.Pref<?>> prefChange = new HashSet<>(
			Arrays.asList(MediaPrefs.VIDEO_SCALE, MediaPrefs.AUDIO_DELAY, MediaPrefs.AUDIO_DELAY_AA,
					MediaPrefs.SUB_DELAY, SUB_SIZE));
	@Nullable
	private SurfaceView videoSurface;
	@Nullable
	private SurfaceView subtitleSurface;
	@Nullable
	private VideoInfoView videoInfoView;
	@Nullable
	private View videoShutter;
	@Nullable
	private VideoOutputCoordinator.SourceLease visibleSource;
	@Nullable
	private MediaEngine callbackFormatEngine;
	@Nullable
	private VideoFormatSnapshot callbackFormat;
	private SubDrawer subDrawer;
	private FutureSupplier<?> createSurface = new Promise<>();
	private long surfaceSizeGeneration;
	private int renderedViewportWidth = Integer.MIN_VALUE;
	private int renderedViewportHeight = Integer.MIN_VALUE;

	public VideoView(Context context) {
		this(context, null);
	}

	public VideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
		getActivity().onSuccess(a -> {
			a.addBroadcastListener(this);
			a.getLib().getPrefs().addBroadcastListener(this);
			setClockPos(a.getPrefs().getClockPosPref());
		});
	}

	protected void init(Context context) {
		setBackgroundColor(Color.BLACK);
		videoSurface = new SurfaceView(getContext()) {
			{
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
				setLayoutParams(lp);
				setOnTouchListener((v, e) -> VideoView.this.onTouchEvent(e));
				getHolder().addCallback(VideoView.this);
			}
		};
		addView(videoSurface);
		videoShutter = new View(getContext());
		videoShutter.setBackgroundColor(Color.BLACK);
		videoShutter.setVisibility(GONE);
		videoShutter.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addView(videoShutter);
		subtitleSurface = new SurfaceView(getContext()) {
			{
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				lp.gravity = Gravity.FILL;
				setLayoutParams(lp);
				setZOrderMediaOverlay(true);
				setZOrderOnTop(true);
				setOnTouchListener((v, e) -> VideoView.this.onTouchEvent(e));
				getHolder().setFormat(PixelFormat.TRANSLUCENT);
				getHolder().addCallback(VideoView.this);
			}
		};
		addView(subtitleSurface);

		addInfoView(context);
		addOnLayoutChangeListener(this);
		setLayoutParams(new CircularRevealFrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		setFocusable(true);
	}

	protected void addInfoView(Context context) {
		VideoInfoView d = new VideoInfoView(context, null);
		d.setVisibility(GONE);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, 0);
		lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		d.setLayoutParams(lp);
		videoInfoView = d;
		addView(d);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (subDrawer == null) return;
		var a = getActivity().peek();
		if (a != null) a.post(this::drawSubtitles);
	}

	/**
	 * Returns the native decoder output Surface, when this host owns one.
	 * Browser-backed video hosts deliberately have no decoder Surface and return {@code null}.
	 */
	@Nullable
	public SurfaceView getVideoSurface() {
		return videoSurface;
	}

	@Nullable
	public SurfaceView getSubtitleSurface() {
		return subtitleSurface;
	}

	/**
	 * Container for browser custom video views. Native video hosts can use this view itself;
	 * specialized hosts may provide a child container without relying on child indexes.
	 */
	@NonNull
	public ViewGroup getContentView() {
		return this;
	}


	public void setClockPos(int pos) {
		int idx = getChildCount() - 1;
		int gravity = Gravity.TOP;

		switch (pos) {
			case MainActivityPrefs.CLOCK_POS_NONE -> {
				if (getChildAt(idx) instanceof TextClock) removeViewAt(idx);
				return;
			}
			case MainActivityPrefs.CLOCK_POS_LEFT -> gravity |= Gravity.START;
			case MainActivityPrefs.CLOCK_POS_RIGHT -> gravity |= Gravity.END;
			case MainActivityPrefs.CLOCK_POS_CENTER -> gravity |= Gravity.CENTER;
		}

		View clock = getChildAt(idx);
		FrameLayout.LayoutParams lp;

		if (clock instanceof TextClock) {
			lp = (FrameLayout.LayoutParams) clock.getLayoutParams();
		} else {
			Context ctx = getContext();
			int m = toIntPx(ctx, 10);
			clock = LayoutInflater.from(ctx).inflate(R.layout.clock_view, this, false);
			lp = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
			lp.setMargins(m, m, m, m);
			addView(clock);
		}

		lp.gravity = gravity;
		clock.setLayoutParams(lp);
	}

	@Nullable
	public VideoInfoView getVideoInfoView() {
		return videoInfoView;
	}

	public void showVideo(boolean hideTitle) {
		createSurface.onSuccess(v -> {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaSessionCallback cb = a.getMediaSessionCallback();
			if (!videoOutput(cb).isSelected(this)) return;
			MediaEngine eng = cb.getEngine();
			if ((eng != null) && videoOutput(cb).isBound(eng)) setSurfaceSize(eng);
			VideoInfoView info = getVideoInfoView();
			if (hideTitle && (info != null)) info.setVisibility(GONE);
		});
	}

	public void prepareSubDrawer(boolean dbl) {
		MainActivityDelegate a = getActivity().peek();
		if (a == null) return;
		var src = a.getMediaSessionCallback().getCurrentItem();
		var ps = (src != null) ? src.getPrefs() : a.getLib().getPrefs();
		var scale = ps.getFloatPref(SUB_SIZE);
		if (dbl) {
			if (subDrawer instanceof DoubleSubDrawer && subDrawer.textScale == scale) return;
			subDrawer = new DoubleSubDrawer(scale);
		} else {
			if (subDrawer instanceof GridDrawer && subDrawer.textScale == scale) return;
			subDrawer = new GridDrawer(scale);
		}
	}

	public void releaseSubDrawer() {
		subDrawer = null;
	}

	@Override
	public void accept(SubGrid.Position position, @Nullable Subtitles.Text text) {
		if (subDrawer == null) return;
		if (!subDrawer.setText(position, text)) return;
		drawSubtitles();
	}

	public void clearVideoSurface() {
		MainActivityDelegate activity = getActivity().peek();
		clearVideoSurface((activity == null) ? null : activity.getMediaSessionCallback().getEngine());
	}

	/** Starts a new output source generation for the engine that is about to render. */
	public void clearVideoSurface(@Nullable MediaEngine expectedEngine) {
		beginVideoSource(expectedEngine);
		VideoOutputCoordinator.SourceLease source = visibleSource;
		FutureSupplier<?> surface = createSurface;
		if (surface == null) return;
		surface.onSuccess(v -> {
			if (!isCurrentSource(expectedEngine, source)) return;
			setShutterVisible(true);
		});
	}

	/**
	 * Associates the next first-frame event with this output without changing the currently visible
	 * frame. Same-item seeks/re-prepares use this path, while item changes also close the shutter.
	 */
	public void beginVideoSource(@Nullable MediaEngine expectedEngine) {
		MainActivityDelegate activity = getActivity().peek();
		if (activity != null) {
			MediaSessionCallback callback = activity.getMediaSessionCallback();
			VideoOutputCoordinator output = videoOutput(callback);
			VideoOutputCoordinator.SourceLease source =
					(expectedEngine == null) ? null : output.beginSource(expectedEngine);
			setVideoSourceLease(source);
			return;
		}
		setVideoSourceLease(null);
	}

	/** Internal coordinator handoff: stage a first-frame lease before this selected output attaches. */
	public void setVideoSourceLease(@Nullable VideoOutputCoordinator.SourceLease source) {
		visibleSource = source;
	}

	/** Reveals decoder output after the new item has rendered its first frame. */
	public void revealVideoSurface() {
		MainActivityDelegate activity = getActivity().peek();
		revealVideoSurface((activity == null) ? null : activity.getMediaSessionCallback().getEngine());
	}

	/** Reveals only when the event belongs to the source generation currently covering the view. */
	public void revealVideoSurface(@Nullable MediaEngine eventEngine) {
		FutureSupplier<?> surface = createSurface;
		if (surface == null) return;
		surface.onSuccess(v -> {
			MainActivityDelegate activity = getActivity().peek();
			if (activity == null) return;
			MediaSessionCallback callback = activity.getMediaSessionCallback();
			VideoOutputCoordinator.SourceLease source = visibleSource;
			if ((eventEngine == null) || (source == null) ||
					!videoOutput(callback).isCurrent(eventEngine, source)) return;
			setShutterVisible(false);
		});
	}

	private void setShutterVisible(boolean visible) {
		if (videoShutter != null) videoShutter.setVisibility(visible ? VISIBLE : GONE);
	}

	private boolean isCurrentSource(@Nullable MediaEngine engine,
			@Nullable VideoOutputCoordinator.SourceLease source) {
		if ((engine == null) || (source == null)) return false;
		MainActivityDelegate activity = getActivity().peek();
		if (activity == null) return false;
		return videoOutput(activity.getMediaSessionCallback()).isSourceCurrent(engine, source);
	}

	/** Clears decoder and sidecar output before a different video owns these surfaces. */
	public void clearPlaybackSurfaces() {
		invalidateRenderMetadata();
		clearVideoSurface();
		clearSubtitleSurface();
		releaseSubDrawer();
	}

	/** Clears decoder and sidecar output for the exact engine selected by the playback lease. */
	public void clearPlaybackSurfaces(@Nullable MediaEngine expectedEngine) {
		invalidateRenderMetadata();
		clearVideoSurface(expectedEngine);
		clearSubtitleSurface();
		releaseSubDrawer();
	}

	/** Makes delayed format retries and callbacks from the previous source inert. */
	private void invalidateRenderMetadata() {
		surfaceSizeGeneration++;
		callbackFormatEngine = null;
		callbackFormat = null;
	}

	public void clearSubtitleSurface() {
		FutureSupplier<?> surface = createSurface;
		if (surface == null) return;
		surface.onSuccess(v -> {
			SurfaceView sv = getSubtitleSurface();
			if (sv == null) return;
			// VLC can own the subtitle Surface as a second native output. Hide it without
			// connecting a competing Canvas producer.
			sv.setAlpha(0f);
		});
	}

	/** Reveals native or app-rendered subtitle output when it becomes current. */
	public void revealSubtitleSurface() {
		createSurface.onSuccess(v -> {
			SurfaceView sv = getSubtitleSurface();
			if (sv != null) sv.setAlpha(1f);
		});
	}

	private void drawSubtitles() {
		createSurface.onSuccess(v -> {
			SurfaceView sv = getSubtitleSurface();
			if (sv == null) return;
			sv.setAlpha(1f);
			var h = sv.getHolder();
			SurfaceCanvas.draw(h, c -> {
				int saveCount = c.save();
				try {
					subDrawer.clr(c);
					subDrawer.draw(c);
				} finally {
					c.restoreToCount(saveCount);
				}
			});
		});
	}

	public void setSurfaceSize(MediaEngine eng) {
		if (!isActiveOutput(eng)) return;
		applySurfaceSize(eng, null, ++surfaceSizeGeneration, 0);
	}

	public void setSurfaceSize(MediaEngine eng, float videoWidth, float videoHeight) {
		// A delayed callback from the engine we just replaced must not overwrite the format
		// fallback (or cancel the retry) belonging to the newly selected decoder output.
		if (!isActiveOutput(eng)) return;
		VideoFormatSnapshot fallback = new VideoFormatSnapshot(videoWidth, videoHeight,
				videoWidth, videoHeight, eng.getVideoPixelWidthHeightRatio());
		callbackFormatEngine = fallback.hasKnownGeometry() ? eng : null;
		callbackFormat = fallback.hasKnownGeometry() ? fallback : null;
		applySurfaceSize(eng, fallback, ++surfaceSizeGeneration, 0);
	}

	private void applySurfaceSize(MediaEngine eng, @Nullable VideoFormatSnapshot fallback,
			long generation, int attempt) {
		if (!isActiveOutput(eng)) return;
		PlayableItem item = eng.getSource();
		if (item == null) return;

		SurfaceView surface = getVideoSurface();
		if (surface == null) return;
		VideoFormatSnapshot format = eng.getVideoFormatSnapshot();
		if (fallback != null) format = format.withFallback(fallback);
		if (!format.hasKnownGeometry() && (callbackFormatEngine == eng) &&
				(callbackFormat != null)) format = callbackFormat;
		else if (format.hasKnownGeometry() && (callbackFormatEngine == eng)) {
			callbackFormatEngine = null;
			callbackFormat = null;
		}
		VideoRenderPlan plan = VideoRenderPlanner.plan(new VideoViewport(getWidth(), getHeight()),
				format, item.getPrefs().getVideoScalePref());
		boolean viewportChanged = (renderedViewportWidth != plan.viewportWidth()) ||
				(renderedViewportHeight != plan.viewportHeight());
		renderedViewportWidth = plan.viewportWidth();
		renderedViewportHeight = plan.viewportHeight();
		applySurfaceLayout(surface, plan.surfaceWidth(), plan.surfaceHeight(), viewportChanged);
		SurfaceView subtitleSurface = getSubtitleSurface();
		if (subtitleSurface != null) {
			applySurfaceLayout(subtitleSurface, plan.contentWidth(), plan.contentHeight(),
					viewportChanged);
		}
		eng.applyVideoRenderPlan(this, plan);
		if (!format.hasKnownGeometry() && (attempt < SURFACE_SIZE_RETRY_DELAYS.length)) {
			postDelayed(() -> {
				if ((generation != surfaceSizeGeneration) || !isActiveOutput(eng)) return;
				applySurfaceSize(eng, null, generation, attempt + 1);
			}, SURFACE_SIZE_RETRY_DELAYS[attempt]);
		}
	}

	static void applySurfaceLayout(SurfaceView surface, int width, int height,
			boolean viewportChanged) {
		ViewGroup.LayoutParams lp = surface.getLayoutParams();
		boolean changed = (lp.width != width) || (lp.height != height);
		FrameLayout.LayoutParams frame = (lp instanceof FrameLayout.LayoutParams value) ? value : null;
		int gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
		if ((frame != null) && (frame.gravity != gravity)) {
			frame.gravity = gravity;
			changed = true;
		}
		// MATCH_PARENT stays -1 across a fullscreen-to-split transition. SurfaceView's native
		// buffer does not reliably notice that parent-bounds change unless its LayoutParams are
		// reapplied, leaving BLAST with the old fullscreen buffer and clipping the new viewport.
		if (!changed && !viewportChanged) return;
		lp.width = width;
		lp.height = height;
		surface.setLayoutParams(lp);
	}

	@Override
	public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
														 int oldTop, int oldRight, int oldBottom) {
		FermataApplication.get().getHandler().post(() -> createSurface.onSuccess(s -> {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaSessionCallback callback = a.getMediaSessionCallback();
			if (!videoOutput(callback).isSelected(this)) return;
			MediaEngine eng = callback.getEngine();
			if (eng == null) return;

			PlayableItem i = eng.getSource();
			if ((i != null) && i.isVideo() && videoOutput(callback).isBound(eng)) setSurfaceSize(eng);
		}));
	}

	private boolean isActiveOutput(MediaEngine eng) {
		MainActivityDelegate activity = getActivity().peek();
		if (activity == null) return false;
		MediaSessionCallback callback = activity.getMediaSessionCallback();
		return videoOutput(callback).isSelected(this) && videoOutput(callback).isBound(eng);
	}

	private static VideoOutputCoordinator videoOutput(MediaSessionCallback callback) {
		return callback.getVideoOutputCoordinator();
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder holder) {
		SurfaceView video = getVideoSurface();
		if ((video == null) || !video.getHolder().getSurface().isValid()) return;
		SurfaceView s = getSubtitleSurface();
		if ((s != null) && !s.getHolder().getSurface().isValid()) return;
		getActivity().onSuccess(
				a -> a.getMediaSessionCallback().addVideoView(this, a.isCarActivityNotMirror() ? 0 : 1));
		if (createSurface instanceof Promise<?> p) {
			createSurface = completedNull();
			p.complete(null);
		}
	}

	@Override
	public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		invalidateRenderMetadata();
		setVideoSourceLease(null);
		createSurface = new Promise<>();
		getActivity().onSuccess(a -> a.getMediaSessionCallback().removeVideoView(this));
	}

	public boolean isSurfaceCreated() {
		return createSurface.isDone();
	}

	public void onSurfaceCreated(Runnable run) {
		createSurface.onSuccess(v -> run.run());
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent e) {
		MainActivityDelegate a = getActivity().peek();
		return (a != null) && a.interceptTouchEvent(e, this::onTouch);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		MainActivityDelegate a;
		FermataServiceUiBinder b;
		ControlPanelView p;

		switch (keyCode) {
			case KEYCODE_ENTER, KEYCODE_DPAD_CENTER -> {
				if ((a = getActivity().peek()) == null) break;
				return a.getControlPanel().onTouch(this);
			}
			case KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT -> {
				if ((a = getActivity().peek()) == null) break;
				p = a.getControlPanel();
				if (!p.isVideoSeekMode() && !a.getBody().isVideoMode()) {
					View v = focusSearch(this, (keyCode == KEYCODE_DPAD_LEFT) ? FOCUS_LEFT : FOCUS_RIGHT);
					if (v != null) {
						v.requestFocus();
						return true;
					} else {
						break;
					}
				}
				b = a.getMediaServiceBinder();
				b.onRwFfButtonClick(keyCode == KEYCODE_DPAD_RIGHT);
				a.getControlPanel().onVideoSeek();
				return true;
			}
			case KEYCODE_DPAD_UP -> {
				if ((a = getActivity().peek()) == null) break;
				b = a.getMediaServiceBinder();
				b.onRwFfButtonLongClick(true);
				a.getControlPanel().onVideoSeek();
				return true;
			}
			case KEYCODE_DPAD_DOWN -> {
				if ((a = getActivity().peek()) == null) break;
				p = a.getControlPanel();
				if (!p.isVideoSeekMode() && isVisible(p)) {
					View v = p.focusSearch();
					if (v != null) {
						v.requestFocus();
						return true;
					} else {
						break;
					}
				}
				b = a.getMediaServiceBinder();
				b.onRwFfButtonLongClick(false);
				a.getControlPanel().onVideoSeek();
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	private boolean onTouch(@NonNull MotionEvent e) {
		MainActivityDelegate a = getActivity().peek();
		if (a == null) return false;
		a.getControlPanel().onVideoViewTouch(this, e);
		return true;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (createSurface.isDone() && !Collections.disjoint(prefChange, prefs)) {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaSessionCallback callback = a.getMediaSessionCallback();
			if (!videoOutput(callback).isSelected(this)) return;
			MediaEngine eng = callback.getEngine();
			if (eng == null) return;
			PlayableItem i = eng.getSource();
			if ((i == null) || !i.isVideo()) return;
			if (!videoOutput(callback).isBound(eng)) return;

			if (prefs.contains(MediaPrefs.VIDEO_SCALE)) {
				setSurfaceSize(eng);
			} else if (prefs.contains(MediaPrefs.AUDIO_DELAY) ||
					prefs.contains(MediaPrefs.AUDIO_DELAY_AA)) {
				eng.setAudioDelay(
						i.getPrefs().getAudioDelayPref(prefs.contains(MediaPrefs.AUDIO_DELAY_AA)));
			} else if (prefs.contains(MediaPrefs.SUB_DELAY)) {
				eng.setSubtitleDelay(i.getPrefs().getSubDelayPref());
			} else if (prefs.contains(SUB_SIZE) && (subDrawer != null)) {
				subDrawer.textScale = i.getPrefs().getFloatPref(SUB_SIZE);
				drawSubtitles();
			}
		}
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			a.getMediaSessionCallback().removeVideoView(this);
			a.getLib().getPrefs().removeBroadcastListener(this);
		}
	}

	@Override
	public View focusSearch(View focused, int direction) {
		MainActivityDelegate a = getActivity().peek();
		if ((a == null) || !a.getBody().isBothMode()) return focused;

		if (direction == FOCUS_LEFT) {
			return MediaItemListView.focusSearchActive(getContext(), focused);
		} else if (direction == FOCUS_RIGHT) {
			NavBarView n = a.getNavBar();
			if (n.isRight()) return n.focusSearch();
		}

		return focused;
	}

	private FutureSupplier<MainActivityDelegate> getActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	private static abstract class SubDrawer {
		float textScale;
		final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		SubDrawer(float textScale) {
			this.textScale = textScale;
			bgPaint.setColor(Color.BLACK);
			bgPaint.setAlpha(135);
		}

		abstract boolean setText(SubGrid.Position position, @Nullable Subtitles.Text text);

		abstract void draw(Canvas canvas);

		void clr(Canvas canvas) {
			canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		}

		float textSize(int canvasHeight, int canvasWidth) {
			var s = textScale * canvasWidth / 25f;
			return canvasHeight > canvasWidth ? s * canvasHeight / canvasWidth : s;
		}

		static TextPaint paint(Paint.Align align) {
			TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
			paint.setColor(Color.WHITE);
			paint.setTypeface(Typeface.DEFAULT);
			paint.setElegantTextHeight(true);
			paint.setTextAlign(align);
			return paint;
		}

		static CharSequence text(String text) {
			var idx = text.indexOf('<');
			if ((idx == -1) || (text.indexOf('>', idx) == -1)) return text;
			return fromHtml(text, FROM_HTML_MODE_LEGACY);
		}

		static StaticLayout layout(CharSequence text, TextPaint paint, int width) {
			return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width).setMaxLines(10)
					.setEllipsize(TextUtils.TruncateAt.END).setIncludePad(true).build();
		}

		void draw(Canvas canvas, StaticLayout sl) {
			float pad = 9f;
			float w = 0f;
			for (int i = 0, n = sl.getLineCount(); i < n; i++) {
				w = Math.max(w, sl.getLineWidth(i));
			}
			w = w / 2f + pad;
			canvas.translate(0, -pad);
			canvas.drawRoundRect(-w, -pad, w, sl.getHeight() + pad, pad, pad, bgPaint);
			sl.draw(canvas);
		}
	}

	private static final class GridDrawer extends SubDrawer {
		private final String[] grid = new String[9];
		private final TextPaint[] paint = new TextPaint[3];
		private final float[] yoff = new float[]{1f, 0.5f, 0.f};


		private GridDrawer(float textScale) {
			super(textScale);
			for (int i = 0; i < 3; i++) {
				paint[i] =
						paint(i == 0 ? Paint.Align.LEFT : i == 1 ? Paint.Align.CENTER : Paint.Align.RIGHT);
			}
		}

		@Override
		public boolean setText(SubGrid.Position position, @Nullable Subtitles.Text text) {
			var t = (text == null) ? null : text.getText();
			int idx = position.ordinal();
			if (Objects.equals(grid[idx], t)) return false;
			grid[idx] = t;
			return true;
		}

		@Override
		public void draw(Canvas canvas) {
			var ch = canvas.getHeight();
			var cw = canvas.getWidth();
			var ts = textSize(ch, cw);
			var x = new int[]{0, cw / 2, cw};
			var y = new int[]{ch, ch / 2, 0};

			for (int i = 0, g = 0; i < 3; i++, g += 3) {
				var l = grid[g] != null;
				var c = grid[g + 1] != null;
				var r = grid[g + 2] != null;
				int[] w = new int[3];

				if (c) {
					if (l) {
						if (r) {
							w[0] = w[1] = w[2] = cw / 3;
						} else {
							w[0] = w[1] = cw / 3;
						}
					} else if (r) {
						w[1] = w[2] = cw / 3;
					} else {
						w[1] = cw;
					}
				} else if (l) {
					if (r) w[0] = w[2] = cw / 2;
					else w[0] = cw;
				} else if (r) {
					w[2] = cw;
				} else {
					continue;
				}

				for (int j = 0; j < 3; j++) {
					if (w[j] == 0) continue;
					var t = text(grid[g + j]);
					paint[j].setTextSize(ts);
					var sl = layout(t, paint[j], w[j]);
					canvas.save();
					canvas.translate(x[j], y[i] - sl.getHeight() * yoff[i]);
					draw(canvas, sl);
					canvas.restore();
				}
			}
		}
	}

	private static final class DoubleSubDrawer extends SubDrawer {
		private final TextPaint paint;
		private boolean center;
		private String text;
		private String translation;

		DoubleSubDrawer(float textScale) {
			super(textScale);
			paint = paint(Paint.Align.CENTER);
		}

		@Override
		boolean setText(SubGrid.Position position, @Nullable Subtitles.Text text) {
			if (position == SubGrid.Position.BOTTOM_LEFT) {
				var t = (text == null) ? null : text.getText();
				if (!center && Objects.equals(this.text, t)) return false;
				center = false;
				this.text = t;
			} else if (position == SubGrid.Position.BOTTOM_RIGHT) {
				var t = (text == null) ? null : text.getText();
				if (!center && Objects.equals(translation, t)) return false;
				center = false;
				translation = t;
			} else {
				center = true;
				var t = (text == null) ? null : text.getText();
				var trans = (text == null) ? null : text.getTranslation();
				var c = position != SubGrid.Position.BOTTOM_CENTER;
				if (center == c && Objects.equals(this.text, t) && Objects.equals(translation, trans))
					return false;
				center = c;
				this.text = t;
				translation = trans;
			}
			return true;
		}

		@Override
		void draw(Canvas canvas) {
			CharSequence sub;

			if (text != null) {
				if (translation != null) {
					var t = text(text).toString();
					sub = t + '\n' + text(translation);
				} else {
					sub = text(text).toString();
				}
			} else if (translation != null) {
				sub = text(translation).toString();
			} else {
				return;
			}

			var ch = canvas.getHeight();
			var cw = canvas.getWidth();
			var x = cw / 2f;

			if (center) {
				float size = (ch > cw) ? cw / 10f : ch / 5f;
				for (; ; ) {
					paint.setTextSize(size);
					var sl = layout(sub, paint, cw);
					var sh = sl.getHeight();
					if (sh < ch) {
						canvas.translate(x, (ch - sl.getHeight()) / 2f);
						draw(canvas, sl);
						break;
					} else {
						size *= 0.9f;
					}
				}
			} else {
				paint.setTextSize(textSize(ch, cw));
				var sl = layout(sub, paint, cw);
				canvas.translate(x, ch - sl.getHeight());
				draw(canvas, sl);
			}
		}
	}
}
