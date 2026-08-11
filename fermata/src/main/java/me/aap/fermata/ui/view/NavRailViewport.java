package me.aap.fermata.ui.view;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import java.util.function.Consumer;

import me.aap.utils.ui.UiUtils;

/** Clipped shared-navigation viewport with passive edge and position affordances. */
final class NavRailViewport extends FrameLayout {
	private static final long SCROLL_DURATION_MS = 180L;
	private static final int FADE_EXTENT_DP = 20;
	private static final int INDICATOR_WIDTH_DP = 3;
	private static final int INDICATOR_INSET_DP = 8;
	private static final int MIN_THUMB_EXTENT_DP = 40;
	private static final int TRACK_ALPHA = 20;
	private static final int THUMB_ALPHA = 194;
	private final FrameLayout clip;
	private final LinearLayoutCompat content;
	private final View topFade;
	private final View bottomFade;
	private final View scrollTrack;
	private final View scrollThumb;
	private final int indicatorInset;
	private final int minThumbExtent;
	private final int indicatorGravity;
	private final Runnable refreshTask = this::refreshScrollState;
	private ValueAnimator animator;
	private int scrollOffset;
	private boolean overflow;

	NavRailViewport(Context context, int tint, boolean rightRail) {
		super(context);
		setClipChildren(true);
		setClipToPadding(true);

		clip = new UnboundedHeightClip(context);
		clip.setClipChildren(true);
		clip.setClipToPadding(true);
		content = new LinearLayoutCompat(context);
		content.setOrientation(LinearLayoutCompat.VERTICAL);
		content.setClipChildren(false);
		clip.addView(content, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		addView(clip, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

		int railColor = resolveThemeColor(context,
				androidx.appcompat.R.attr.colorPrimary, Color.BLACK);
		int fadeExtent = UiUtils.toIntPx(context, FADE_EXTENT_DP);
		topFade = createFade(context, railColor, true);
		bottomFade = createFade(context, railColor, false);
		addView(topFade, new FrameLayout.LayoutParams(MATCH_PARENT, fadeExtent, Gravity.TOP));
		addView(bottomFade, new FrameLayout.LayoutParams(MATCH_PARENT, fadeExtent, Gravity.BOTTOM));

		indicatorInset = UiUtils.toIntPx(context, INDICATOR_INSET_DP);
		minThumbExtent = UiUtils.toIntPx(context, MIN_THUMB_EXTENT_DP);
		indicatorGravity = (rightRail ? Gravity.START : Gravity.END) | Gravity.TOP;
		int indicatorWidth = UiUtils.toIntPx(context, INDICATOR_WIDTH_DP);
		float indicatorRadius = indicatorWidth;
		scrollTrack = createIndicator(context,
				ColorUtils.setAlphaComponent(tint, TRACK_ALPHA), indicatorRadius);
		scrollThumb = createIndicator(context,
				ColorUtils.setAlphaComponent(tint, THUMB_ALPHA), indicatorRadius);

		var trackLp = new FrameLayout.LayoutParams(indicatorWidth, MATCH_PARENT,
				indicatorGravity);
		trackLp.topMargin = indicatorInset;
		trackLp.bottomMargin = indicatorInset;
		addView(scrollTrack, trackLp);
		addView(scrollThumb, new FrameLayout.LayoutParams(indicatorWidth,
				minThumbExtent, indicatorGravity));
		setAffordancesVisible(false);
	}

	void addNavigationItem(View item) {
		content.addView(item);
		post(refreshTask);
	}

	void clearNavigationItems() {
		cancelAnimator();
		content.removeAllViews();
		scrollOffset = 0;
		content.setTranslationY(0F);
		post(refreshTask);
	}

	void forEachNavigationItem(Consumer<View> consumer) {
		for (int i = 0, count = content.getChildCount(); i < count; i++) {
			consumer.accept(content.getChildAt(i));
		}
	}

	boolean contains(View view) {
		return (view != null) && (view.getParent() == content);
	}

	boolean scrollByDistance(float distanceY) {
		int max = getMaxScroll();
		if (max <= 0) return false;
		cancelAnimator();
		setScrollOffset(NavRailScrollPolicy.nextScroll(scrollOffset, distanceY, max));
		return true;
	}

	void ensureVisible(View view, boolean animate) {
		if (!contains(view) || (clip.getHeight() <= 0)) return;
		int target = scrollOffset;
		if (view.getTop() < scrollOffset) target = view.getTop();
		else if (view.getBottom() > scrollOffset + clip.getHeight()) {
			target = view.getBottom() - clip.getHeight();
		}
		target = clamp(target, getMaxScroll());
		if (target == scrollOffset) return;
		if (animate) animateTo(target);
		else {
			cancelAnimator();
			setScrollOffset(target);
		}
	}

	void refreshScrollState() {
		if (!isAttachedToWindow() || (clip.getHeight() <= 0)) return;
		overflow = content.getHeight() > clip.getHeight();
		setScrollOffset(clamp(scrollOffset, getMaxScroll()));
		updateAffordances();
	}

	private void animateTo(int target) {
		target = clamp(target, getMaxScroll());
		if (target == scrollOffset) {
			updateAffordances();
			return;
		}
		cancelAnimator();
		if (!ValueAnimator.areAnimatorsEnabled()) {
			setScrollOffset(target);
			return;
		}
		ValueAnimator next = ValueAnimator.ofInt(scrollOffset, target);
		animator = next;
		next.setDuration(SCROLL_DURATION_MS);
		next.addUpdateListener(a -> setScrollOffset((Integer) a.getAnimatedValue()));
		next.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				if (animator != animation) return;
				animator = null;
				updateAffordances();
			}
		});
		next.start();
	}

	private void setScrollOffset(int offset) {
		scrollOffset = clamp(offset, getMaxScroll());
		content.setTranslationY(-scrollOffset);
		updateAffordances();
	}

	private void updateAffordances() {
		if (!overflow) {
			setAffordancesVisible(false);
			return;
		}

		int max = getMaxScroll();
		topFade.setVisibility(NavRailScrollPolicy.canScrollUp(scrollOffset) ?
				VISIBLE : GONE);
		bottomFade.setVisibility(NavRailScrollPolicy.canScrollDown(scrollOffset, max) ?
				VISIBLE : GONE);
		scrollTrack.setVisibility(VISIBLE);
		scrollThumb.setVisibility(VISIBLE);

		int trackHeight = Math.max(0, clip.getHeight() - (indicatorInset * 2));
		int contentHeight = content.getHeight();
		int thumbHeight = (contentHeight <= 0) ? trackHeight : Math.round(
				trackHeight * (clip.getHeight() / (float) contentHeight));
		thumbHeight = Math.min(trackHeight, Math.max(minThumbExtent, thumbHeight));
		int travel = Math.max(0, trackHeight - thumbHeight);
		int top = indicatorInset + ((max <= 0) ? 0 : Math.round(
				travel * (scrollOffset / (float) max)));
		var lp = (FrameLayout.LayoutParams) scrollThumb.getLayoutParams();
		if ((lp.height != thumbHeight) || (lp.topMargin != top) ||
				(lp.gravity != indicatorGravity)) {
			lp.height = thumbHeight;
			lp.topMargin = top;
			lp.gravity = indicatorGravity;
			scrollThumb.setLayoutParams(lp);
		}
	}

	private void setAffordancesVisible(boolean visible) {
		int visibility = visible ? VISIBLE : GONE;
		topFade.setVisibility(visibility);
		bottomFade.setVisibility(visibility);
		scrollTrack.setVisibility(visibility);
		scrollThumb.setVisibility(visibility);
	}

	private int getMaxScroll() {
		return Math.max(0, content.getHeight() - clip.getHeight());
	}

	private static int clamp(int value, int max) {
		return Math.max(0, Math.min(Math.max(0, max), value));
	}

	private void cancelAnimator() {
		if (animator == null) return;
		ValueAnimator current = animator;
		animator = null;
		current.cancel();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		post(refreshTask);
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(refreshTask);
		cancelAnimator();
		super.onDetachedFromWindow();
	}

	private static View createFade(Context context, int color, boolean top) {
		View fade = new View(context);
		int transparent = ColorUtils.setAlphaComponent(color, 0);
		int[] colors = top ? new int[]{color, transparent} :
				new int[]{transparent, color};
		fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors));
		fade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
		return fade;
	}

	private static View createIndicator(Context context, int color, float radius) {
		View indicator = new View(context);
		GradientDrawable background = new GradientDrawable();
		background.setColor(color);
		background.setCornerRadius(radius);
		indicator.setBackground(background);
		indicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
		return indicator;
	}

	private static int resolveThemeColor(Context context, int attr, int fallback) {
		TypedValue value = new TypedValue();
		if (!context.getTheme().resolveAttribute(attr, value, true)) return fallback;
		return (value.resourceId == 0) ? value.data :
				ContextCompat.getColor(context, value.resourceId);
	}

	/** Keeps the viewport bounded while allowing its vertical content to report true overflow. */
	private static final class UnboundedHeightClip extends FrameLayout {
		UnboundedHeightClip(Context context) {
			super(context);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int width = MeasureSpec.getSize(widthMeasureSpec);
			int height = MeasureSpec.getSize(heightMeasureSpec);
			int childWidth = MeasureSpec.makeMeasureSpec(
					Math.max(0, width - getPaddingLeft() - getPaddingRight()),
					MeasureSpec.EXACTLY);
			int childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			for (int i = 0, count = getChildCount(); i < count; i++) {
				View child = getChildAt(i);
				if (child.getVisibility() != GONE) child.measure(childWidth, childHeight);
			}
			setMeasuredDimension(resolveSize(width, widthMeasureSpec),
					resolveSize(height, heightMeasureSpec));
		}
	}
}
