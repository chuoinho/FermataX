package me.aap.fermata.addon.web.tiktok;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.web.R;

/**
 * Floating capsule pill navigation bar for TikTok Web player.
 * Ported and modernized from CarStream Tech into FermataX design system.
 * Contains Up Chevron (previous video), Dynamic Circular Center Button (play/pause),
 * and Down Chevron (next video). Supports touch-dragging anywhere across the screen.
 */
public class TikTokFloatingNavView extends FrameLayout implements TikTokJsBridge.Listener {
	private ImageButton btnPrev;
	private ImageButton btnPlayPause;
	private ImageButton btnNext;
	@Nullable
	private TikTokWebView webView;
	private boolean isPlaying = false;
	private float downX, downY;
	private float downTranslationX, downTranslationY;
	private boolean isDragging = false;
	private int touchSlop;

	public TikTokFloatingNavView(@NonNull Context context) {
		super(context);
		init(context);
	}

	public TikTokFloatingNavView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public TikTokFloatingNavView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		LayoutInflater.from(context).inflate(R.layout.tiktok_nav_capsule, this, true);
		btnPrev = findViewById(R.id.tiktokBtnPrev);
		btnPlayPause = findViewById(R.id.tiktokBtnPlayPause);
		btnNext = findViewById(R.id.tiktokBtnNext);

		btnPrev.setOnClickListener(v -> {
			if (webView != null) {
				TikTokMediaSessionBridge bridge = webView.getMediaSessionBridge();
				if (bridge != null) {
					bridge.dispatchPrev();
				} else {
					webView.evaluateJavascript("(function(){ if (typeof window.__cntPrev === 'function') window.__cntPrev(); })()", null);
				}
			}
		});

		btnPlayPause.setOnClickListener(v -> {
			if (webView != null) {
				TikTokMediaSessionBridge bridge = webView.getMediaSessionBridge();
				if (bridge != null) {
					bridge.dispatchPlayPause();
				} else {
					webView.evaluateJavascript("(function(){ var v = (typeof window.__cntVideo === 'function' ? window.__cntVideo() : document.querySelector('video')); if (v) { if (v.paused) v.play(); else v.pause(); } })()", null);
				}
			}
		});

		btnNext.setOnClickListener(v -> {
			if (webView != null) {
				TikTokMediaSessionBridge bridge = webView.getMediaSessionBridge();
				if (bridge != null) {
					bridge.dispatchNext();
				} else {
					webView.evaluateJavascript("(function(){ if (typeof window.__cntNext === 'function') window.__cntNext(); })()", null);
				}
			}
		});

		updatePlayPauseIcon(false);
	}

	public void attach(@Nullable TikTokWebView web) {
		detach();
		this.webView = web;
		if (web != null) {
			TikTokJsBridge js = web.getJsBridge();
			if (js != null) {
				js.addListener(this);
			}
			TikTokMediaSessionBridge mediaBridge = web.getMediaSessionBridge();
			if (mediaBridge != null) {
				updatePlayPauseIcon(mediaBridge.getState().isPlaying());
			}
		}
	}

	public void detach() {
		if (webView != null) {
			TikTokJsBridge js = webView.getJsBridge();
			if (js != null) {
				js.removeListener(this);
			}
			webView = null;
		}
	}

	public void updatePlayPauseIcon(boolean playing) {
		this.isPlaying = playing;
		post(() -> {
			if (btnPlayPause != null) {
				btnPlayPause.setImageResource(playing ? R.drawable.ic_tiktok_pause : R.drawable.ic_tiktok_play);
			}
		});
	}

	@Override
	public void onPlayingChanged(boolean playing) {
		updatePlayPauseIcon(playing);
	}

	@Override
	public void onNowPlayingChanged(String id, String name, String art, long positionMs,
			long durationMs, boolean playing) {
		updatePlayPauseIcon(playing);
	}

	public boolean isPlaying() {
		return isPlaying;
	}

	@Nullable
	public ImageButton getBtnPrev() {
		return btnPrev;
	}

	@Nullable
	public ImageButton getBtnPlayPause() {
		return btnPlayPause;
	}

	@Nullable
	public ImageButton getBtnNext() {
		return btnNext;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = ev.getRawX();
				downY = ev.getRawY();
				downTranslationX = getTranslationX();
				downTranslationY = getTranslationY();
				isDragging = false;
				break;
			case MotionEvent.ACTION_MOVE:
				float dx = ev.getRawX() - downX;
				float dy = ev.getRawY() - downY;
				if (Math.hypot(dx, dy) > touchSlop) {
					isDragging = true;
					return true;
				}
				break;
		}
		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = ev.getRawX();
				downY = ev.getRawY();
				downTranslationX = getTranslationX();
				downTranslationY = getTranslationY();
				isDragging = false;
				return true;
			case MotionEvent.ACTION_MOVE:
				float dx = ev.getRawX() - downX;
				float dy = ev.getRawY() - downY;
				if (!isDragging && Math.hypot(dx, dy) > touchSlop) {
					isDragging = true;
				}
				if (isDragging) {
					setTranslationX(downTranslationX + dx);
					setTranslationY(downTranslationY + dy);
					return true;
				}
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (isDragging) {
					isDragging = false;
					clampToParent();
					return true;
				}
				break;
		}
		return super.onTouchEvent(ev);
	}

	private void clampToParent() {
		ViewGroup parent = (ViewGroup) getParent();
		if (parent == null) return;
		int parentWidth = parent.getWidth();
		int parentHeight = parent.getHeight();
		int viewWidth = getWidth();
		int viewHeight = getHeight();
		if (parentWidth <= 0 || parentHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return;

		float currentLeft = getLeft() + getTranslationX();
		float currentTop = getTop() + getTranslationY();

		float clampedLeft = Math.max(0, Math.min(currentLeft, parentWidth - viewWidth));
		float clampedTop = Math.max(0, Math.min(currentTop, parentHeight - viewHeight));

		setTranslationX(clampedLeft - getLeft());
		setTranslationY(clampedTop - getTop());
	}
}
