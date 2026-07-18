package me.aap.fermata.ui.view;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;

import me.aap.utils.ui.UiUtils;

/** Expands touch handling without changing the measured or drawn bounds of target views. */
public final class MinimumTouchTargetDelegate extends TouchDelegate {
	static final int MIN_TARGET_DP = 48;
	private final ViewGroup delegateView;
	private final View[] targets;
	private TouchDelegate activeDelegate;

	private MinimumTouchTargetDelegate(ViewGroup delegateView, View[] targets) {
		super(new Rect(), delegateView);
		this.delegateView = delegateView;
		this.targets = targets;
	}

	public static void install(ViewGroup delegateView, View... targets) {
		View[] valid = Arrays.stream(targets).filter(target -> target != null).toArray(View[]::new);
		delegateView.setTouchDelegate(new MinimumTouchTargetDelegate(delegateView, valid));
		for (View target : valid) target.setAccessibilityDelegate(
				new MinimumTargetAccessibilityDelegate());
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) activeDelegate = findDelegate(event);
		TouchDelegate delegate = activeDelegate;
		if (delegate == null) return false;

		boolean handled = delegate.onTouchEvent(event);
		if ((action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL))
			activeDelegate = null;
		return handled;
	}

	private TouchDelegate findDelegate(MotionEvent event) {
		int count = targets.length;
		int[][] actual = new int[count][4];
		int[][] expanded = new int[count][4];
		int minSize = UiUtils.toIntPx(delegateView.getContext(), MIN_TARGET_DP);
		Rect bounds = new Rect();

		for (int i = 0; i < count; i++) {
			View target = targets[i];
			if (!target.isShown() || !target.isEnabled() || !target.isClickable()) continue;
			bounds.set(0, 0, target.getWidth(), target.getHeight());
			delegateView.offsetDescendantRectToMyCoords(target, bounds);
			actual[i] = new int[]{bounds.left, bounds.top, bounds.right, bounds.bottom};
			expanded[i] = expandBounds(bounds.left, bounds.top, bounds.right, bounds.bottom,
					minSize, delegateView.getWidth(), delegateView.getHeight());
		}

		int index = selectTargetIndex(actual, expanded, (int) event.getX(), (int) event.getY());
		if (index < 0) return null;
		int[] hit = expanded[index];
		return new TouchDelegate(new Rect(hit[0], hit[1], hit[2], hit[3]), targets[index]);
	}

	static int[] expandBounds(int left, int top, int right, int bottom, int minSize,
			int parentWidth, int parentHeight) {
		int[] horizontal = expandAxis(left, right, minSize, parentWidth);
		int[] vertical = expandAxis(top, bottom, minSize, parentHeight);
		return new int[]{horizontal[0], vertical[0], horizontal[1], vertical[1]};
	}

	static int selectTargetIndex(int[][] actual, int[][] expanded, int x, int y) {
		for (int i = 0; i < actual.length; i++) {
			if (contains(actual[i], x, y)) return i;
		}

		int selected = -1;
		long nearest = Long.MAX_VALUE;
		for (int i = 0; i < expanded.length; i++) {
			if (!contains(expanded[i], x, y)) continue;
			int[] bounds = actual[i];
			long dx = (2L * x) - bounds[0] - bounds[2];
			long dy = (2L * y) - bounds[1] - bounds[3];
			long distance = (dx * dx) + (dy * dy);
			if (distance < nearest) {
				nearest = distance;
				selected = i;
			}
		}
		return selected;
	}

	private static int[] expandAxis(int start, int end, int minSize, int parentSize) {
		int deficit = Math.max(0, minSize - (end - start));
		int expandedStart = start - (deficit / 2);
		int expandedEnd = end + deficit - (deficit / 2);

		if (expandedStart < 0) {
			expandedEnd = Math.min(parentSize, expandedEnd - expandedStart);
			expandedStart = 0;
		}
		if (expandedEnd > parentSize) {
			expandedStart = Math.max(0, expandedStart - (expandedEnd - parentSize));
			expandedEnd = parentSize;
		}
		return new int[]{expandedStart, expandedEnd};
	}

	private static boolean contains(int[] bounds, int x, int y) {
		return (bounds[2] > bounds[0]) && (bounds[3] > bounds[1]) &&
				(x >= bounds[0]) && (x < bounds[2]) && (y >= bounds[1]) && (y < bounds[3]);
	}

	private static final class MinimumTargetAccessibilityDelegate extends View.AccessibilityDelegate {
		@Override
		public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
			super.onInitializeAccessibilityNodeInfo(host, info);
			if (!host.isShown() || !host.isEnabled() || !host.isClickable()) return;
			if (!(host.getParent() instanceof View parent)) return;
			int minSize = UiUtils.toIntPx(host.getContext(), MIN_TARGET_DP);
			int[] bounds = expandBounds(host.getLeft(), host.getTop(), host.getRight(),
					host.getBottom(), minSize, parent.getWidth(), parent.getHeight());
			info.setBoundsInParent(new Rect(bounds[0], bounds[1], bounds[2], bounds[3]));
		}
	}
}
