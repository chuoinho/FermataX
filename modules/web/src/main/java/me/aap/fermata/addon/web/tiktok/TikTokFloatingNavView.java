package me.aap.fermata.addon.web.tiktok;

import android.content.res.ColorStateList;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.addon.web.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.utils.pref.PreferenceStore;

/**
 * Floating capsule pill navigation bar for TikTok Web player.
 * Ported and modernized from CarStream Tech into FermataX design system.
 * Contains Up Chevron (previous video), Dynamic Circular Center Button (play/pause),
 * and Down Chevron (next video). Supports touch-dragging anywhere across the screen.
 */
public class TikTokFloatingNavView extends FrameLayout implements TikTokJsBridge.Listener,
		PreferenceStore.Listener {
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
		applyTheme();
	}

	public boolean isGlassTheme() {
		try {
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if (a != null) return a.getPrefs().isGlassTheme(a.isCarActivity());
			return MainActivityPrefs.get().isGlassTheme(true) || MainActivityPrefs.get().isGlassTheme(false);
		} catch (Throwable e) {
			return false;
		}
	}

	public void applyTheme() {
		boolean glass = isGlassTheme();
		View root = findViewById(R.id.tiktokNavCapsuleRoot);
		if (!glass) {
			if (root != null) root.setBackgroundResource(R.drawable.bg_tiktok_nav_pill);
			if (btnPrev != null) {
				btnPrev.setImageTintList(null);
				btnPrev.setBackgroundResource(R.drawable.bg_tiktok_nav_btn);
			}
			if (btnPlayPause != null) {
				btnPlayPause.setImageTintList(null);
				btnPlayPause.setBackgroundResource(R.drawable.bg_tiktok_center_btn);
			}
			if (btnNext != null) {
				btnNext.setImageTintList(null);
				btnNext.setBackgroundResource(R.drawable.bg_tiktok_nav_btn);
			}
			return;
		}

		Context ctx = getContext();
		boolean isNight = GlassThemeColors.isNight(ctx);
		GlassThemeColors colors = GlassThemeColors.get(ctx);

		if (root != null) {
			root.setBackground(createCapsuleBackground(ctx, isNight));
			root.setElevation(GlassDimensions.dpToPx(ctx, 4));
		}

		int chevronColor = isNight ? 0xFFFFFFFF : 0xFF15181A;
		int actionColor = isNight ? GlassThemeColors.ACCENT_CYAN : colors.getActionText();

		if (btnPrev != null) {
			btnPrev.setImageTintList(ColorStateList.valueOf(chevronColor));
			btnPrev.setBackground(createChevronButtonBackground(ctx, isNight));
		}

		if (btnPlayPause != null) {
			btnPlayPause.setImageTintList(ColorStateList.valueOf(actionColor));
			btnPlayPause.setBackground(createCenterButtonBackground(ctx, isNight));
		}

		if (btnNext != null) {
			btnNext.setImageTintList(ColorStateList.valueOf(chevronColor));
			btnNext.setBackground(createChevronButtonBackground(ctx, isNight));
		}
	}

	private Drawable createCapsuleBackground(Context ctx, boolean isNight) {
		return createGlassPillDrawable(ctx, isNight);
	}

	public static Drawable createGlassPillDrawable(Context ctx, boolean isNight) {
		float density = ctx.getResources().getDisplayMetrics().density;
		GradientDrawable d = new GradientDrawable();
		d.setShape(GradientDrawable.RECTANGLE);
		d.setCornerRadius(26f * density);
		int bg = isNight ? 0xE6151A22 : 0xEEF2F1EF;
		int stroke = isNight ? 0x28FFFFFF : 0x20000000;
		d.setColor(bg);
		d.setStroke(Math.max(1, (int) density), stroke);
		return d;
	}

	private Drawable createCenterButtonBackground(Context ctx, boolean isNight) {
		float density = ctx.getResources().getDisplayMetrics().density;
		GradientDrawable shape = new GradientDrawable();
		shape.setShape(GradientDrawable.OVAL);
		int fill = isNight ? 0x2E00E5FF : 0x22007A87;
		int stroke = isNight ? 0x6600E5FF : 0x44007A87;
		shape.setColor(fill);
		shape.setStroke(Math.max(1, (int) density), stroke);

		int rippleColor = isNight ? 0x4000E5FF : 0x33007A87;
		GradientDrawable mask = new GradientDrawable();
		mask.setShape(GradientDrawable.OVAL);
		mask.setColor(0xFFFFFFFF);
		return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, mask);
	}

	private Drawable createChevronButtonBackground(Context ctx, boolean isNight) {
		int rippleColor = isNight ? 0x26FFFFFF : 0x1A000000;
		GradientDrawable mask = new GradientDrawable();
		mask.setShape(GradientDrawable.OVAL);
		mask.setColor(0xFFFFFFFF);
		return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		try {
			MainActivityPrefs.get().addBroadcastListener(this);
		} catch (Throwable ignored) {}
		applyTheme();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		try {
			MainActivityPrefs.get().removeBroadcastListener(this);
		} catch (Throwable ignored) {}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		post(this::applyTheme);
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
				if (isGlassTheme()) {
					boolean isNight = GlassThemeColors.isNight(getContext());
					int actionColor = isNight ? GlassThemeColors.ACCENT_CYAN : GlassThemeColors.get(getContext()).getActionText();
					btnPlayPause.setImageTintList(ColorStateList.valueOf(actionColor));
				}
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
