package me.aap.fermata.ui.view;

import static me.aap.fermata.BuildConfig.AUTO;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.GestureDetectorCompat;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.view.GestureListener;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.NavButtonView;

/**
 * @author Andrey Pavlenko
 */
public class FermataNavBarView extends NavBarView implements GestureListener {
	private final GestureDetectorCompat gestureDetector;
	private final Paint fadePaint = new Paint();
	private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path chevronPath = new Path();
	private final Runnable refreshScrollStateTask = this::refreshScrollState;
	private int touchSlop;
	private int fadeExtent;
	private float touchDownX;
	private float touchDownY;
	private View touchTargetChild;
	private boolean suppressClickUntilUp;
	private boolean nudgeScheduled;
	private Runnable nudgeTask;
	private ObjectAnimator nudgeAnimator;

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		gestureDetector = new GestureDetectorCompat(context, this);
		init();
	}

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		gestureDetector = new GestureDetectorCompat(context, this);
		init();
	}

	private void init() {
		touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		fadeExtent = UiUtils.toIntPx(getContext(), 42);
		chevronPaint.setStyle(Paint.Style.STROKE);
		chevronPaint.setStrokeWidth(UiUtils.toIntPx(getContext(), 2));
		chevronPaint.setStrokeCap(Paint.Cap.ROUND);
		chevronPaint.setStrokeJoin(Paint.Join.ROUND);
		setClipToPadding(true);
		setWillNotDraw(false);
		setVerticalScrollBarEnabled(false);
		if (AUTO && MainActivityDelegate.get(getContext()).isCarActivity()) {
			setBackgroundResource(isRight() ? R.drawable.aa_nav_rail_bg_right :
					R.drawable.aa_nav_rail_bg_left);
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent e) {
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				touchDownX = e.getX();
				touchDownY = e.getY();
				touchTargetChild = findTouchedChild(e);
				suppressClickUntilUp = false;
			}
			case MotionEvent.ACTION_MOVE -> {
				if (shouldSuppressClickForGesture(e)) {
					if (!suppressClickUntilUp) {
						suppressClickUntilUp = true;
						dispatchCancelToPressedChild(e);
					}
					gestureDetector.onTouchEvent(e);
					return true;
				}
			}
			case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				if (suppressClickUntilUp) {
					gestureDetector.onTouchEvent(e);
					suppressClickUntilUp = false;
					touchTargetChild = null;
					return true;
				}
				suppressClickUntilUp = false;
				touchTargetChild = null;
			}
		}

		return super.dispatchTouchEvent(e);
	}

	protected boolean interceptTouchEvent(MotionEvent e) {
		gestureDetector.onTouchEvent(e);
		return super.onTouchEvent(e);
	}

	private boolean shouldSuppressClickForGesture(MotionEvent e) {
		if (suppressClickUntilUp) return true;
		if (touchSlop <= 0) return false;

		float dx = Math.abs(e.getX() - touchDownX);
		float dy = Math.abs(e.getY() - touchDownY);
		return (dx > touchSlop) || (dy > touchSlop);
	}

	private void dispatchCancelToPressedChild(MotionEvent src) {
		if (touchTargetChild == null) return;
		MotionEvent cancel = MotionEvent.obtain(src);
		cancel.setAction(MotionEvent.ACTION_CANCEL);
		touchTargetChild.dispatchTouchEvent(cancel);
		cancel.recycle();
	}

	@Nullable
	private View findTouchedChild(MotionEvent e) {
		float x = e.getX() + getScrollX();
		float y = e.getY() + getScrollY();
		for (int i = getChildCount() - 1; i >= 0; i--) {
			View child = getChildAt(i);
			if ((child.getVisibility() == VISIBLE) && (x >= child.getLeft()) && (x < child.getRight()) &&
					(y >= child.getTop()) && (y < child.getBottom())) {
				return child;
			}
		}
		return null;
	}

	@Override
	protected MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public void addView(View child) {
		super.addView(child);
		sizeVerticalNavButton(child, getVerticalButtonExtent());
		post(refreshScrollStateTask);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		sizeVerticalNavButtons(w);
		post(refreshScrollStateTask);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		sizeVerticalNavButtons(MeasureSpec.getSize(widthMeasureSpec));
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		sizeVerticalNavButtons(r - l);
		super.onLayout(changed, l, t, r, b);
		refreshScrollState();
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		drawScrollAffordance(canvas);
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		invalidate();
	}

	@Override
	public void onActivityEvent(ActivityDelegate a, long e) {
		super.onActivityEvent(a, e);
		if ((e == FRAGMENT_CHANGED) || (e == FRAGMENT_CONTENT_CHANGED))
			post(refreshScrollStateTask);
	}

	@Override
	public boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeLeft(e1, e2);
	}

	@Override
	public boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeRight(e1, e2);
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if (scrollNavBar(distanceY)) return true;
		return getMainActivity().getControlPanel().onScroll(e1, e2, distanceX, distanceY);
	}

	private boolean scrollNavBar(float distanceY) {
		int max = getMaxScrollY();
		if (max == 0) return false;
		int next = clamp(getScrollY() + (int) distanceY, 0, max);
		if (next == getScrollY()) return true;
		scrollTo(0, next);
		return true;
	}

	private int getContentHeight() {
		int count = getChildCount();
		if (count == 0) return 0;
		View child = getChildAt(count - 1);
		return child.getBottom() + getPaddingBottom();
	}

	private int getMaxScrollY() {
		return Math.max(0, getContentHeight() - getHeight());
	}

	private void refreshScrollState() {
		if (!isAttachedToWindow()) return;
		ensureActiveItemVisible();
		scheduleFirstRunNudge();
		invalidate();
	}

	private void ensureActiveItemVisible() {
		if (getHeight() <= 0) return;
		View active = findViewById(getMainActivity().getActiveNavItemId());
		if (active == null) return;

		int top = active.getTop();
		int bottom = active.getBottom();
		int scroll = getScrollY();
		int next = scroll;

		if (top < scroll) {
			next = top;
		} else {
			int visibleBottom = scroll + getHeight();
			if (bottom > visibleBottom) next = bottom - getHeight();
		}

		next = clamp(next, 0, getMaxScrollY());
		if (next != scroll) scrollTo(0, next);
	}

	private void scheduleFirstRunNudge() {
		if (!AUTO || nudgeScheduled || (getMaxScrollY() <= 0)) return;

		MainActivityDelegate a = getMainActivity();
		if (!a.isCarActivity()) return;
		MainActivityPrefs prefs = a.getPrefs();
		if (prefs.getBooleanPref(MainActivityPrefs.NAV_BAR_SCROLL_NUDGE_AA)) return;

		nudgeScheduled = true;
		prefs.applyBooleanPref(MainActivityPrefs.NAV_BAR_SCROLL_NUDGE_AA, true);
		nudgeTask = () -> {
			nudgeTask = null;
			if (!isAttachedToWindow()) return;
			int start = getScrollY();
			int max = getMaxScrollY();
			if ((max <= 0) || (getVisibility() != VISIBLE)) return;
			int peek = Math.min(max, Math.max(UiUtils.toIntPx(getContext(), 24), getHeight() / 8));
			nudgeAnimator = ObjectAnimator.ofInt(this, "scrollY", start, peek, start);
			nudgeAnimator.setDuration(700).start();
		};
		postDelayed(nudgeTask, 700);
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(refreshScrollStateTask);
		if (nudgeTask != null) {
			removeCallbacks(nudgeTask);
			nudgeTask = null;
		}
		if (nudgeAnimator != null) {
			nudgeAnimator.cancel();
			nudgeAnimator = null;
		}
		super.onDetachedFromWindow();
	}

	private void drawScrollAffordance(Canvas canvas) {
		int max = getMaxScrollY();
		if (max <= 0) return;

		int width = getWidth();
		int height = getHeight();
		if ((width <= 0) || (height <= 0)) return;

		int scroll = getScrollY();
		int save = canvas.save();
		canvas.translate(0, scroll);
		if (scroll > 0) drawIndicator(canvas, true, width, height);
		if (scroll < max) drawIndicator(canvas, false, width, height);
		canvas.restoreToCount(save);
	}

	private void drawIndicator(Canvas canvas, boolean top, int width, int height) {
		int extent = Math.min(fadeExtent, height / 3);
		if (extent <= 0) return;

		int color = getIndicatorBaseColor();
		int transparent = color & 0x00FFFFFF;
		fadePaint.setShader(top ?
				new LinearGradient(0, 0, 0, extent, color, transparent, Shader.TileMode.CLAMP) :
				new LinearGradient(0, height - extent, 0, height, transparent, color,
						Shader.TileMode.CLAMP));
		canvas.drawRect(0, top ? 0 : height - extent, width, top ? extent : height, fadePaint);
		fadePaint.setShader(null);

		chevronPaint.setColor(getChevronColor());
		float cx = width / 2f;
		float cy = top ? extent * 0.38f : height - (extent * 0.38f);
		float half = Math.max(4f, width * 0.16f);
		float drop = Math.max(4f, extent * 0.18f);
		chevronPath.reset();
		if (top) {
			chevronPath.moveTo(cx - half, cy + drop);
			chevronPath.lineTo(cx, cy - drop);
			chevronPath.lineTo(cx + half, cy + drop);
		} else {
			chevronPath.moveTo(cx - half, cy - drop);
			chevronPath.lineTo(cx, cy + drop);
			chevronPath.lineTo(cx + half, cy - drop);
		}
		canvas.drawPath(chevronPath, chevronPaint);
	}

	private int getIndicatorBaseColor() {
		int color = getBgColor();
		if (Color.alpha(color) == 0) return Color.argb(210, 8, 18, 32);
		return Color.argb(210, Color.red(color), Color.green(color), Color.blue(color));
	}

	private int getChevronColor() {
		int color = getTint();
		if (Color.alpha(color) == 0) return Color.argb(210, 125, 160, 220);
		return Color.argb(220, Color.red(color), Color.green(color), Color.blue(color));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private int getVerticalButtonExtent() {
		int extent = getWidth();
		if (extent <= 0) {
			var lp = getLayoutParams();
			if (lp != null) extent = lp.width;
		}
		return (extent <= 0) ? extent : getVerticalButtonExtent(extent);
	}

	private void sizeVerticalNavButtons(int extent) {
		extent = (extent <= 0) ? getVerticalButtonExtent() : getVerticalButtonExtent(extent);
		if (extent <= 0) return;
		for (int i = 0, n = getChildCount(); i < n; i++) {
			sizeVerticalNavButton(getChildAt(i), extent);
		}
	}

	private int getVerticalButtonExtent(int navBarWidth) {
		return (navBarWidth * 7) / 8;
	}

	private void sizeVerticalNavButton(View child, int extent) {
		if (!(child instanceof NavButtonView button) || (extent <= 0)) return;

		var lp = child.getLayoutParams();
		if (lp == null) return;
		boolean changed = (lp.width != extent) || (lp.height != extent);
		lp.width = extent;
		lp.height = extent;

		int verticalMargin = UiUtils.toIntPx(getContext(), 4);
		if (lp instanceof LinearLayoutCompat.LayoutParams llp) {
			if (llp.weight != 0F) changed = true;
			llp.weight = 0F;
			llp.gravity = Gravity.CENTER_HORIZONTAL;
			if ((llp.topMargin != verticalMargin) || (llp.bottomMargin != verticalMargin)) changed = true;
			llp.setMargins(0, verticalMargin, 0, verticalMargin);
		} else if (lp instanceof LinearLayout.LayoutParams llp) {
			if (llp.weight != 0F) changed = true;
			llp.weight = 0F;
			llp.gravity = Gravity.CENTER_HORIZONTAL;
			if ((llp.topMargin != verticalMargin) || (llp.bottomMargin != verticalMargin)) changed = true;
			llp.setMargins(0, verticalMargin, 0, verticalMargin);
		}

		if (AUTO && getMainActivity().isCarActivity()) {
			int iconPadding = Math.max(UiUtils.toIntPx(getContext(), 5), extent / 8);
			button.setIconPadding(iconPadding);
			button.setBackgroundResource(isRight() ? R.drawable.aa_nav_button_bg_right :
					R.drawable.aa_nav_button_bg_left);
		}

		if (changed) child.setLayoutParams(lp);
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
